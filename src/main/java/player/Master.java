package player;

import connection.AckSender;
import connection.MessageResender;
import model.Food;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.lang.Thread.sleep;

public class Master extends Observable implements Player {
    private int masterId;

    //id увеличивается при создании нового игрока
    private int id = 0;

    //stateOrder увеличивается каждую итерацию таймера
    private int stateOrder = 0;

    //увеличивается с каждой отправкой уникального GameMessage
    private Long msgSeq = 0L;

    private final DatagramSocket socket;
    private Thread announceThread;
    private MessageResender messageResender;
    private final SnakeProto.GameConfig.Builder gameConfig;
    private final Map<InetSocketAddress, SnakeProto.GameMessage> steerMessages = new HashMap<>();
    private final Map<InetSocketAddress, SnakeProto.GameMessage> joinMessages = new HashMap<>();
    private final Map<InetSocketAddress, Long> lastMsgTime = new HashMap<>();
    private final Map<InetSocketAddress, SnakeProto.GamePlayer.Builder> players = new HashMap<>();
    private final Map<InetSocketAddress, Snake> snakes = new HashMap<>();
    private final Snake playerSnake;
    private InetSocketAddress[][] field;
    private final Food food;
    private final String name;
    private final GamePanel gamePanel;

    public Master(GamePanel gamePanel,
                  SnakeProto.GameConfig.Builder settings,
                  String name,
                  DatagramSocket socket,
                  MessageResender messageResender) {
        this.gamePanel = gamePanel;
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
        masterId = getNextId();
        playerSnake = new Snake(gameConfig.getWidth(), gameConfig.getHeight(), masterId, coords, SnakeProto.Direction.UP);
        snakes.put((InetSocketAddress) socket.getLocalSocketAddress(), playerSnake);
        SnakeProto.GamePlayer.Builder gamePlayer = SnakeProto.GamePlayer.newBuilder();
        gamePlayer.setName(name)
                .setId(masterId)
                .setIpAddress(socket.getLocalAddress().toString())
                .setPort(socket.getPort())
                .setRole(SnakeProto.NodeRole.MASTER)
                .setScore(0);
        players.put((InetSocketAddress) socket.getLocalSocketAddress(), gamePlayer);

        food = new Food(gameConfig.getFoodStatic(),
                (int) gameConfig.getFoodPerPlayer(),
                gameConfig.getWidth(),
                gameConfig.getHeight());

        gamePanel.setGameSize(gameConfig.getWidth(), gameConfig.getHeight());
        gamePanel.setPlayer(this);
        gamePanel.setFood(food);
        gamePanel.setKeyBindings();
        addObserver((Observer) gamePanel);
        gamePanel.setPlaying(true);
    }

    private void incrementStateOrder() {
        stateOrder++;
    }

    private void incrementMessageSeq() {
        msgSeq++;
    }

    private int getNextId() {
        return ++id;
    }

    public void checkSnakesCollision() {
        field = new InetSocketAddress[gameConfig.getWidth()][gameConfig.getHeight()];
        snakes.forEach((addr, snake) -> {
            SnakeProto.GameState.Coord head = snake.getCoords().get(0);
            if (field[head.getX()][head.getY()] != null) {
                snake.kill();
                snakes.get(field[head.getX()][head.getY()]).kill();
            } else {
                field[head.getX()][head.getY()] = addr;
            }
        });
        snakes.forEach((addr, snake) -> {
            List<SnakeProto.GameState.Coord> points = snake.getCoords();
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

    private void updatePanelState(SnakeProto.GameState state) {
        List<Snake> sList = new ArrayList<>();
        state.getSnakesList().forEach(snake -> {
            Snake s = new Snake(SnakeProto.GameState.Snake.newBuilder(snake),
                    gameConfig.getWidth(), gameConfig.getHeight());
            s.unpackCoords();
            s.setCoords(s.getUnpackedCoords());
            sList.add(s);
        });
        Food food = new Food(state.getFoodsList());
        gamePanel.setFood(food);
        gamePanel.setPlayers(state.getPlayers().getPlayersList());
        gamePanel.setSnakes(sList);
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
        SnakeProto.Direction direction = null;
        SnakeProto.GameState.Coord.Builder tail = SnakeProto.GameState.Coord.newBuilder();
        switch (new Random().nextInt(4)) {
            case 0: {
                direction = SnakeProto.Direction.UP;
                tail.setX(x).setY(y + 1);
                break;
            }
            case 1: {
                direction = SnakeProto.Direction.DOWN;
                tail.setX(x).setY(y - 1);
                break;
            }
            case 2: {
                direction = SnakeProto.Direction.RIGHT;
                tail.setX(x - 1).setY(y);
                break;
            }
            case 3: {
                direction = SnakeProto.Direction.LEFT;
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

    private void joinNewPlayers() {
        joinMessages.forEach((addr, gameMessage) -> {
            if ((!snakes.containsKey(addr)) && (addNewSnakeIfEnoughSpace(addr))) {
                SnakeProto.GamePlayer.Builder gamePlayer = SnakeProto.GamePlayer.newBuilder();
                gamePlayer.setName(gameMessage.getJoin().getName())
                        .setId(snakes.get(addr).getPlayerId())
                        .setIpAddress(addr.getAddress().toString())
                        .setPort(addr.getPort())
                        .setRole(SnakeProto.NodeRole.NORMAL)
                        .setScore(0);
                players.put(addr, gamePlayer);
                try {
                    AckSender.sendAck(addr, gameMessage.getMsgSeq(), socket, masterId, players.get(addr).getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void addJoinMsg(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        joinMessages.putIfAbsent(socketAddress, gameMsg);
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

    private void deletePlayer(InetSocketAddress address) {
        players.remove(address);
        messageResender.removeReceiver(address);
    }

    private void checkLastMessageTime() {
        Iterator<Map.Entry<InetSocketAddress, Long>> it = lastMsgTime.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<InetSocketAddress, Long> lastTime = it.next();
            if (Instant.now().toEpochMilli()  - lastTime.getValue() > gameConfig.getNodeTimeoutMs()) {
                deletePlayer(lastTime.getKey());
                it.remove();
            }
        }
    }

    private void sendAck(InetSocketAddress socketAddress, Long msgSeq) throws IOException {
        AckSender.sendAck(socketAddress, msgSeq, socket, masterId, players.get(socketAddress).getId());
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
            snake.packCoords();
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
                .setMsgSeq(msgSeq);

        return gameMessage.build();
    }

    private void sendStateMessages(SnakeProto.GameMessage stateMessage) {
        byte[] buf = stateMessage.toByteArray();
        players.forEach((addr, player) -> {
            if (player.getId() != masterId) {
                DatagramPacket packet =
                        new DatagramPacket(buf, buf.length, addr.getAddress(), addr.getPort());
                try {
                    socket.send(packet);
                    messageResender.setMessagesToResend(addr, msgSeq, stateMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
                socket.setSoTimeout(gameConfig.getStateDelayMs() -
                        (int) Duration.between(startTime, endTime).toMillis());
                socket.receive(packet);
                byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
                SnakeProto.GameMessage gameMsg = SnakeProto.GameMessage.parseFrom(msg);

                if (gameMsg.hasJoin()) {
                    updateLastMessageTime((InetSocketAddress) packet.getSocketAddress());
                    addJoinMsg((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                    continue;
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
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                endTime = Instant.now();
            }
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
    public void steer(SnakeProto.Direction direction) {
        playerSnake.setNextDirection(direction);
    }

    @Override
    public void start() {
        Timer timer = new Timer();
        sendAnnounce();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                SnakeProto.GameMessage stateMessage = makeStateMessage();
                updatePanelState(stateMessage.getState().getState());
                sendStateMessages(stateMessage);
                incrementStateOrder();
                recvMessages();
                checkLastMessageTime();
                //role change message
                updateState();
                joinNewPlayers();
                setChanged();
                notifyObservers();
            }
        }, 0, gameConfig.getStateDelayMs());
    }
}
