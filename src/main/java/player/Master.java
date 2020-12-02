package player;

import connection.MessageResender;
import model.Food;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.lang.Thread.sleep;

public class Master extends Observable implements Player {
    private int id = 0;
    private int stateOrder = 0;
    private int messageSeq = 0;
    private final DatagramSocket socket;
    private Thread announceThread;
    private MessageResender messageResender;
    private final SnakeProto.GameConfig.Builder gameConfig;
    private final Map<InetSocketAddress, SnakeProto.GameMessage> steerMessages = new HashMap<>();
    private final Map<InetSocketAddress, SnakeProto.GameMessage.JoinMsg> joinMessages = new HashMap<>();
    private final Map<InetSocketAddress, Long> lastMsgTime = new HashMap<>();
    private final Map<InetSocketAddress, SnakeProto.GamePlayer.Builder> players = new HashMap<>();
    private final Map<InetSocketAddress, Snake> snakes = new HashMap<>();
    InetSocketAddress[][] field;
    private final Food food;
    private final String name;

    public Master(GamePanel gamePanel,
                  SnakeProto.GameConfig.Builder settings,
                  String name,
                  DatagramSocket socket,
                  MessageResender messageResender) {
        this.socket = socket;
        this.name = name;
        this.gameConfig = settings.clone();
        this.messageResender = messageResender;

        List<SnakeProto.GameState.Coord> coords = new ArrayList<>();
        coords.add(SnakeProto.GameState.Coord.newBuilder()
                .setX(gameConfig.getWidth() / 2)
                .setY(gameConfig.getHeight() / 2).build());
        coords.add(SnakeProto.GameState.Coord.newBuilder()
                .setX(gameConfig.getWidth() / 2)
                .setY(gameConfig.getHeight() / 2 + 1).build());
        Snake snake = new Snake(gameConfig.getWidth(), gameConfig.getHeight(), getNextId(), coords, SnakeProto.Direction.UP);
        snakes.put((InetSocketAddress) socket.getLocalSocketAddress(), snake);

        food = new Food(gameConfig.getFoodStatic(),
                (int) gameConfig.getFoodPerPlayer(),
                gameConfig.getWidth(),
                gameConfig.getHeight());

        gamePanel.setGameSize(gameConfig.getWidth(), gameConfig.getHeight());
        gamePanel.setSnake(snakes.get(socket.getLocalSocketAddress())); // тоже List
        gamePanel.setFood(food);
        gamePanel.setKeyBindings();
        addObserver((Observer) gamePanel);
        gamePanel.setPlaying(true);
    }

    private void incrementStateOrder() {
        stateOrder++;
    }

    private void incrementMessageSeq() {
        messageSeq++;
    }

    private int getNextId() {
        return ++id;
    }

    public void checkSnakesCollision() {
        field = new InetSocketAddress[gameConfig.getWidth()][gameConfig.getHeight()];
        snakes.forEach((addr, snake) -> {
            SnakeProto.GameState.Coord head = snake.getUnpackedCoords().get(0);
            if (field[head.getX()][head.getY()] != null) {
                snake.kill();
                snakes.get(field[head.getX()][head.getY()]).kill();
            } else {
                field[head.getX()][head.getY()] = addr;
            }
        });
        snakes.forEach((addr, snake) -> {
            List<SnakeProto.GameState.Coord> points = snake.getUnpackedCoords();
            for (int i = 1; i < points.size(); i++) {
                if (field[points.get(i).getX()][points.get(i).getY()] != null) {
                    if (addr != field[points.get(i).getX()][points.get(i).getY()]) {
                        players.get(addr).setScore(players.get(addr).getScore() + 1);
                    }
                    snakes.get(field[points.get(i).getX()][points.get(i).getY()]).kill();
                } else {
                    field[points.get(i).getX()][points.get(i).getY()] = addr;
                }
            }
        });
    }

    private void updateState() {
        snakes.forEach((addr, snake) -> {
            if (steerMessages.containsKey(addr)) {
                snake.setNextDirection(steerMessages.get(addr).getSteer().getDirection());
            }
            snake.moveForward(food);
        });
        checkSnakesCollision();
        snakes.forEach((addr, snake) -> snake.toFood(food, gameConfig.getDeadFoodProb()));
        food.updateFood(new ArrayList<>(snakes.values()));
    }

    private boolean isEmptySquare(int x, int y) {
        for (int i = -2; i < 2; i++) {
            for (int j = -2; j < 2; j++) {
                if (field[(x + i < 0) ? (gameConfig.getWidth() + i) : ((x + i) % gameConfig.getWidth())]
                        [(y + j < 0) ? (gameConfig.getHeight() + j) : ((y + j) % gameConfig.getHeight())] != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void addNewSnake(int x, int y, InetSocketAddress addr) {
        SnakeProto.Direction direction = SnakeProto.Direction.forNumber(new Random().nextInt(4));
        SnakeProto.GameState.Coord.Builder tail = SnakeProto.GameState.Coord.newBuilder();
        switch (Objects.requireNonNull(direction)) {
            case UP: {
                tail.setX(x).setY(y + 1);
                break;
            }
            case DOWN: {
                tail.setX(x).setY(y - 1);
                break;
            }
            case RIGHT: {
                tail.setX(x - 1).setY(y);
                break;
            }
            case LEFT: {
                tail.setX(x + 1).setY(y);
            }
        }
        List<SnakeProto.GameState.Coord> coords = new ArrayList<>();
        coords.add(SnakeProto.GameState.Coord.newBuilder().setX(x).setY(y).build());
        coords.add(tail.build());
        Snake snake = new Snake(gameConfig.getWidth(), gameConfig.getHeight(), getNextId(), coords, direction);
        snakes.put(addr, snake);
    }

    private boolean addNewSnakeIfEnoughSpace(InetSocketAddress addr) {
        food.addToCheckField(field);
        for (int i = 0; i < gameConfig.getWidth(); i += 3) {
            for (int j = 0; j < gameConfig.getHeight(); j += 3) {
                if (isEmptySquare(i, j)) {
                    addNewSnake(i, j, addr);
                    return true;
                }
            }
        }
        return false;
    }

    private void joinNewPlayers() { // добавить ответ
        joinMessages.forEach((addr, joinMsg) -> {
            if (addNewSnakeIfEnoughSpace(addr)) {
                SnakeProto.GamePlayer.Builder gamePlayer = SnakeProto.GamePlayer.newBuilder();
                gamePlayer.setName(joinMsg.getName())
                        .setId(snakes.get(addr).getPlayerId())
                        .setIpAddress(addr.getAddress().toString())
                        .setPort(addr.getPort())
                        .setRole(SnakeProto.NodeRole.NORMAL)
                        .setScore(0);
                players.put(addr, gamePlayer);
            }
        });
    }

    private void addJoinMsg(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        joinMessages.putIfAbsent(socketAddress, gameMsg.getJoin());
    }

    private void updateLastMessageTime(InetSocketAddress socketAddress) {
        lastMsgTime.put(socketAddress, Instant.now().toEpochMilli());
    }

    private void updateAcks(InetSocketAddress socketAddress, Long msgSeq) {
        messageResender.removeMessage(socketAddress, msgSeq);
    }

    private void addSteerMsg(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        if (steerMessages.containsKey(socketAddress)) {
            if (steerMessages.get(socketAddress).getMsgSeq() >= gameMsg.getMsgSeq()) {
                return;
            }
        }
        steerMessages.put(socketAddress, gameMsg);
    }

    private void changePlayerRole(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        steerMessages.remove(socketAddress);
        //смена роли NORMAL -> VIEWER
    }

    private void sendAck(InetSocketAddress socketAddress, Long msgSeq) throws IOException {
        SnakeProto.GameMessage.AckMsg ackMsg = SnakeProto.GameMessage.AckMsg.newBuilder().build();
        SnakeProto.GameMessage gameMessage = SnakeProto.GameMessage.newBuilder()
                .setAck(ackMsg)
                .setMsgSeq(msgSeq)
                .build();
        byte[] buf = gameMessage.toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, socketAddress.getAddress(), socketAddress.getPort());
        socket.send(packet);
    }

    private SnakeProto.GameMessage makeStateMessage() {
        List<SnakeProto.GameState.Snake> snakesProto = new ArrayList<>();
        snakes.forEach((addr, snake) -> {
            SnakeProto.GameState.Snake.Builder snakeBuilder = SnakeProto.GameState.Snake.newBuilder();
            if (snake.isAlive()) {
                snakeBuilder.setState(SnakeProto.GameState.Snake.SnakeState.ALIVE);
            } else {
                snakeBuilder.setState(SnakeProto.GameState.Snake.SnakeState.ZOMBIE);
            }
            snakeBuilder.setPlayerId(players.get(addr).getId());
            snakeBuilder.addAllPoints(snake.getPackedCoords());
            snakeBuilder.setHeadDirection(snake.getDirection());
            snakesProto.add(snakeBuilder.build());
        });

        List<SnakeProto.GamePlayer> gamePlayers = new ArrayList<>();
        players.forEach((addr, player) -> gamePlayers.add(player.build()));

        SnakeProto.GameState.Builder gameState = SnakeProto.GameState.newBuilder();
        gameState.setStateOrder(stateOrder)
                .addAllSnakes(snakesProto)
                .addAllFoods(food.getFoods())
                .setPlayers(SnakeProto.GamePlayers.newBuilder().addAllPlayers(gamePlayers).build())
                .setConfig(gameConfig);
        SnakeProto.GameMessage.Builder gameMessage = SnakeProto.GameMessage.newBuilder();
        gameMessage.setState(SnakeProto.GameMessage.StateMsg.newBuilder().setState(gameState).build())
                .setMsgSeq(messageSeq);

        return gameMessage.build();
    }

    private void sendStateMessages() {
        byte[] buf = makeStateMessage().toByteArray();
        players.forEach((addr, player)-> {
            DatagramPacket packet =
                    new DatagramPacket(buf, buf.length, addr.getAddress(), addr.getPort());
            try {
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        incrementMessageSeq();
    }


    private void recvMessages() {
        Instant startTime = Instant.now();
        steerMessages.clear();
        joinMessages.clear();
        Instant endTime = Instant.now();
        while (Duration.between(startTime, endTime).toMillis() < gameConfig.getStateDelayMs()) {
            byte[] buf = new byte[128];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.setSoTimeout((int) Duration.between(startTime, endTime).toMillis());
                socket.receive(packet);
                byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                SnakeProto.GameMessage gameMsg = SnakeProto.GameMessage.parseFrom(msg);

                if (gameMsg.hasJoin()) {
                    updateLastMessageTime((InetSocketAddress) packet.getSocketAddress());
                    addJoinMsg((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                } else if (gameMsg.hasPing()) {
                    updateLastMessageTime((InetSocketAddress) packet.getSocketAddress());
                } else if (gameMsg.hasAck()) {
                    updateAcks((InetSocketAddress) packet.getSocketAddress(), gameMsg.getMsgSeq());
                } else if (gameMsg.hasSteer()) {
                    updateLastMessageTime((InetSocketAddress) packet.getSocketAddress());
                    addSteerMsg((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                } else if (gameMsg.hasRoleChange()) {
                    updateLastMessageTime((InetSocketAddress) packet.getSocketAddress());
                    changePlayerRole((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                }

                sendAck((InetSocketAddress) packet.getSocketAddress(), gameMsg.getMsgSeq());
            } catch (IOException e) {
                e.printStackTrace();
            }
            endTime = Instant.now();
        }
    }

    private void sendAnnounce() {
        announceThread = new Thread(() -> {
            while (!announceThread.isInterrupted()) {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                byte[] buf;
                List<SnakeProto.GamePlayer> gamePlayers = new ArrayList<>();
                gamePlayers.add(SnakeProto.GamePlayer.newBuilder()
                        .setName(name)
                        .setId(1)
                        .setIpAddress("")
                        .setPort(socket.getPort())
                        .setRole(SnakeProto.NodeRole.MASTER)
                        .setScore(5).build());

                SnakeProto.GameMessage.AnnouncementMsg.Builder announceBuilder
                        = SnakeProto.GameMessage.AnnouncementMsg.newBuilder();
                announceBuilder.setPlayers(SnakeProto.GamePlayers.newBuilder().addAllPlayers(gamePlayers));
                announceBuilder.setConfig(gameConfig);
                SnakeProto.GameMessage gameMessage = SnakeProto.GameMessage.newBuilder()
                        .setAnnouncement(announceBuilder)
                        .setMsgSeq(1)
                        .build();

                buf = gameMessage.toByteArray();
                try {
                    DatagramPacket packet =
                            new DatagramPacket(buf, buf.length, InetAddress.getByName("239.192.0.4"), 9192);
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        });
        announceThread.start();
    }

    @Override
    public void stop() {
        announceThread.interrupt();
        messageResender.interrupt();
    }

    @Override
    public void start() {
        Timer timer = new Timer();
        sendAnnounce();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                recvMessages();
                //role change message
                updateState();
                joinNewPlayers();
                sendStateMessages();
                incrementStateOrder();
                setChanged();
                notifyObservers();
            }
        }, 0, gameConfig.getStateDelayMs());
    }
}
