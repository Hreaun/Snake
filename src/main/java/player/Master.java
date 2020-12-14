package player;

import connection.AckSender;
import connection.MessageResender;
import model.Food;
import model.Game;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Master extends Observable implements Player {
    private final int masterId;

    //id увеличивается при создании нового игрока
    private int id = 0;

    //stateOrder увеличивается каждую итерацию таймера
    private int stateOrder = 0;

    //увеличивается с каждой отправкой уникального GameMessage
    private Long msgSeq = 0L;

    ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);
    private final Game game;
    private final DatagramSocket socket;
    private Timer announceThread;
    private final MessageResender messageResender;
    private final SnakeProto.GameConfig.Builder gameConfig;
    private final Map<InetSocketAddress, SnakeProto.GameMessage> steerMessages = new HashMap<>();
    private final Map<InetSocketAddress, SnakeProto.GameMessage> joinMessages = new HashMap<>();
    private final Map<InetSocketAddress, Long> lastMsgTime = new HashMap<>();
    private final Map<InetSocketAddress, SnakeProto.GamePlayer.Builder> players = new HashMap<>();
    private final Map<InetSocketAddress, Snake> snakes = new HashMap<>();
    private Snake playerSnake;
    private InetSocketAddress[][] field;
    private Food food;
    private String name;
    private final GamePanel gamePanel;

    public Master(Game game, GamePanel gamePanel,
                  SnakeProto.GameConfig.Builder settings,
                  String name,
                  DatagramSocket socket,
                  MessageResender messageResender) {
        this.game = game;
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
        gamePanel.setPlayerId(masterId);
        gamePanel.setFood(food);
        gamePanel.setKeyBindings();
        addObserver((Observer) gamePanel);
        gamePanel.setPlaying(true);
    }

    public Master(Game game, SnakeProto.GameMessage state, int masterId, GamePanel gamePanel,
                  SnakeProto.GameConfig.Builder settings,
                  DatagramSocket socket, MessageResender messageResender) {
        this.game = game;
        this.masterId = masterId;
        this.gamePanel = gamePanel;
        this.socket = socket;
        this.gameConfig = settings.clone();
        this.messageResender = messageResender;


        unpackGameState(state);
        setStateOrder(state.getState().getState().getStateOrder());
        setMsgSeq(state.getMsgSeq());

        players.forEach((addr, player) -> {
            if (player.getId() != masterId) {
                try {
                    sendRoleChangeMessage(addr, player.getRole());
                } catch (IOException e) {
                    game.displayErrorAndStopGame(e);
                }
            }
        });

        gamePanel.setGameSize(gameConfig.getWidth(), gameConfig.getHeight());
        gamePanel.setPlayer(this);
        gamePanel.setPlayerId(masterId);
        gamePanel.setFood(food);
        gamePanel.setKeyBindings();
        addObserver((Observer) gamePanel);
        gamePanel.setPlaying(true);
    }

    private void setMsgSeq(long msgSeq) {
        this.msgSeq = msgSeq;
    }

    private void setStateOrder(int stateOrder) {
        this.stateOrder = stateOrder;
    }

    private void setCurrentId(int id) {
        this.id = id;
    }

    private void unpackGameState(SnakeProto.GameMessage state) {
        Map<Integer, SnakeProto.GamePlayer> playersMap = new HashMap<>();
        AtomicInteger maxPlayerId = new AtomicInteger();
        AtomicInteger oldMasterId = new AtomicInteger();
        state.getState().getState().getPlayers().getPlayersList().forEach(player -> {
            if (player.getId() > maxPlayerId.get()) {
                maxPlayerId.set(player.getId());
            }
            if (player.getRole() != SnakeProto.NodeRole.MASTER) {
                playersMap.put(player.getId(), player);
            } else {
                oldMasterId.set(player.getId());
            }
            if (player.getId() == masterId) {
                players.put((InetSocketAddress) socket.getLocalSocketAddress(),
                        SnakeProto.GamePlayer.newBuilder(player));
                players.get(socket.getLocalSocketAddress()).setRole(SnakeProto.NodeRole.MASTER);
                name = player.getName();
            } else {
                if (player.getRole() != SnakeProto.NodeRole.MASTER) {
                    players.put(new InetSocketAddress(player.getIpAddress(), player.getPort()),
                            SnakeProto.GamePlayer.newBuilder(player));
                }
            }
        });
        setCurrentId(maxPlayerId.get());

        AtomicInteger zombieSnakeCounter = new AtomicInteger();
        state.getState().getState().getSnakesList().forEach(snake -> {
            Snake s = new Snake(SnakeProto.GameState.Snake.newBuilder(snake),
                    gameConfig.getWidth(), gameConfig.getHeight());
            s.unpackCoords();
            s.setCoords(s.getUnpackedCoords());

            if (snake.getPlayerId() == oldMasterId.get()) {
                s.makeZombie();
            }

            if (snake.getPlayerId() == masterId) {
                snakes.put((InetSocketAddress) socket.getLocalSocketAddress(), s);
                playerSnake = s;
            } else if (playersMap.containsKey(snake.getPlayerId())) {
                snakes.put(new InetSocketAddress(playersMap.get(snake.getPlayerId()).getIpAddress(),
                        playersMap.get(snake.getPlayerId()).getPort()), s);
            } else {
                snakes.put(new InetSocketAddress(zombieSnakeCounter.getAndIncrement()), s);
            }
        });
        food = new Food(gameConfig.getFoodStatic(),
                (int) gameConfig.getFoodPerPlayer(),
                gameConfig.getWidth(),
                gameConfig.getHeight(), state.getState().getState().getFoodsList());
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

        //сначала выставляются головы змей
        snakes.forEach((addr, snake) -> {
            SnakeProto.GameState.Coord head = snake.getCoords().get(0);
            if (field[head.getX()][head.getY()] != null) {

                //т.к. выставлены только головы, умирают обе змеи
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
                        if (players.containsKey(addr)) {
                            players.get(addr).setScore(players.get(addr).getScore() + 1);
                        }
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
        game.displayScore(getPlayerScoreMap(state), masterId);
    }

    private Map<SnakeProto.GamePlayer, Integer> getPlayerScoreMap(SnakeProto.GameState state) {
        Map<SnakeProto.GamePlayer, Integer> playerScoreMap = new HashMap<>();
        state.getPlayers().getPlayersList().forEach(player -> {
            playerScoreMap.put(player, player.getScore());
        });
        return playerScoreMap;
    }

    private void updateState() {
        snakes.forEach((addr, snake) -> {
            if (steerMessages.containsKey(addr)) {
                snake.setNextDirection(steerMessages.get(addr).getSteer().getDirection());
            }
            snake.moveForward(food);
            if ((snake.ateFood()) && (players.containsKey(addr))) {
                players.get(addr).setScore(players.get(addr).getScore() + 1);
            }
        });
        checkSnakesCollision();
        snakes.forEach((addr, snake) -> snake.toFood(food, gameConfig.getDeadFoodProb()));
        changeDeadSnakePlayerRole();
        removeDeadSnakes();
        food.updateFood(new ArrayList<>(snakes.values()));
    }

    private void changeDeadSnakePlayerRole() {
        snakes.forEach((addr, snake) -> {
            if ((!snake.isAlive()) && (snake.getPlayerId() == masterId)) {
                game.changeToViewer();
            } else if ((!snake.isAlive())
                    && (players.containsKey(addr))) {
                players.get(addr).setRole(SnakeProto.NodeRole.VIEWER);
                try {
                    checkDeputy();
                    sendRoleChangeMessage(addr, SnakeProto.NodeRole.VIEWER);
                } catch (IOException e) {
                    game.displayErrorAndStopGame(e);
                }
            }
        });
    }

    private void removeDeadSnakes() {
        snakes.entrySet().removeIf(snakesSet -> !snakesSet.getValue().isAlive());
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

    private void putNewPlayer(SnakeProto.GameMessage gameMessage, InetSocketAddress addr,
                              SnakeProto.NodeRole role, int playerId) {
        SnakeProto.GamePlayer.Builder gamePlayer = SnakeProto.GamePlayer.newBuilder();
        gamePlayer.setName(gameMessage.getJoin().getName())
                .setId(playerId)
                .setIpAddress(addr.getHostName())
                .setPort(addr.getPort())
                .setRole(role)
                .setScore(0);
        players.put(addr, gamePlayer);
    }

    private void joinNewPlayers() {
        joinMessages.forEach((addr, gameMessage) -> {
            if (gameMessage.getJoin().getOnlyView()) {
                putNewPlayer(gameMessage, addr, SnakeProto.NodeRole.VIEWER, getNextId());
                try {
                    AckSender.sendAck(addr, gameMessage.getMsgSeq(), socket, masterId, players.get(addr).getId());
                } catch (IOException e) {
                    game.displayErrorAndStopGame(e);
                }
            } else if ((!snakes.containsKey(addr)) && (addNewSnakeIfEnoughSpace(addr))) {
                putNewPlayer(gameMessage, addr, SnakeProto.NodeRole.NORMAL, snakes.get(addr).getPlayerId());
                checkDeputy();
                try {
                    AckSender.sendAck(addr, gameMessage.getMsgSeq(), socket, masterId, players.get(addr).getId());
                } catch (IOException e) {
                    game.displayErrorAndStopGame(e);
                }
            }
        });
    }

    private void sendRoleChangeMessage(InetSocketAddress address, SnakeProto.NodeRole role) throws IOException {
        SnakeProto.GameMessage.RoleChangeMsg.Builder roleChangeMsg = SnakeProto.GameMessage.RoleChangeMsg.newBuilder();
        roleChangeMsg
                .setReceiverRole(role)
                .setSenderRole(SnakeProto.NodeRole.MASTER);
        if (role == SnakeProto.NodeRole.MASTER) {
            roleChangeMsg.setSenderRole(SnakeProto.NodeRole.VIEWER);
        }

        SnakeProto.GameMessage.Builder gameMessage = SnakeProto.GameMessage.newBuilder();
        gameMessage.setRoleChange(roleChangeMsg.build())
                .setMsgSeq(msgSeq)
                .setSenderId(masterId)
                .setReceiverId(players.get(address).getId());

        SnakeProto.GameMessage message = gameMessage.build();
        byte[] buf = message.toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, address.getAddress(), address.getPort());
        socket.send(packet);
        messageResender.setMessagesToResend(address, msgSeq, message);
        incrementMessageSeq();
    }

    private void checkDeputy() {
        AtomicBoolean noDeputy = new AtomicBoolean(true);
        players.forEach((addr, player) -> {
            if (player.getRole() == SnakeProto.NodeRole.DEPUTY) {
                noDeputy.set(false);
            }
        });
        if (noDeputy.get()) {
            snakes.forEach((addr, snake) -> {
                if ((snake.getState() != SnakeProto.GameState.Snake.SnakeState.ZOMBIE)
                        && (snake.getPlayerId() != masterId)) {
                    players.get(addr).setRole(SnakeProto.NodeRole.DEPUTY);
                    try {
                        sendRoleChangeMessage(addr, SnakeProto.NodeRole.DEPUTY);
                    } catch (IOException e) {
                        game.displayErrorAndStopGame(e);
                    }
                }
            });

        }

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

        if (snakes.containsKey(socketAddress)) {
            snakes.get(socketAddress).makeZombie();
        }

        if ((players.containsKey(socketAddress))) {
            if (players.get(socketAddress).getRole() == SnakeProto.NodeRole.DEPUTY) {
                players.get(socketAddress).setRole(gameMsg.getRoleChange().getSenderRole());
                checkDeputy();
            } else {
                players.get(socketAddress).setRole(gameMsg.getRoleChange().getSenderRole());
            }
        }
    }

    private void deletePlayer(InetSocketAddress address) {
        boolean isDeputy = false;
        if (players.get(address).getRole() == SnakeProto.NodeRole.DEPUTY) {
            isDeputy = true;
        }
        players.remove(address);
        if (isDeputy) {
            checkDeputy();
        }
        messageResender.removeReceiver(address);
    }

    private void makeZombieSnake(InetSocketAddress address) {
        if (snakes.containsKey(address)) {
            snakes.get(address).makeZombie();
        }
    }

    private void checkLastMessageTime() {
        Iterator<Map.Entry<InetSocketAddress, Long>> it = lastMsgTime.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<InetSocketAddress, Long> lastTime = it.next();
            if (Instant.now().toEpochMilli() - lastTime.getValue() > gameConfig.getNodeTimeoutMs()) {
                makeZombieSnake(lastTime.getKey());
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
            snakeBuilder.setState(SnakeProto.GameState.Snake.SnakeState.ALIVE);
            snakeBuilder.setPlayerId(snake.getPlayerId());
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
                    game.displayErrorAndStopGame(e);
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
            } catch (SocketException e) {
                return;
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                game.displayErrorAndStopGame(e);
            } finally {
                endTime = Instant.now();
            }
        }
    }

    private void sendAnnounce() {
        announceThread = new Timer();

        announceThread.schedule(new TimerTask() {
            @Override
            public void run() {
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
                    game.displayErrorAndStopGame(e);
                }
            }
        }, 0, 1_000);
    }

    @Override
    public void stop() {
        timer.shutdown();
        try {
            timer.awaitTermination(gameConfig.getStateDelayMs() * 2, TimeUnit.MILLISECONDS);
            if (players.size() > 1) {
                sendRoleChangeToDeputy();
            }
            announceThread.cancel();
            messageResender.interrupt();
            messageResender.join();
        } catch (InterruptedException e) {
            game.displayErrorAndStopGame(e);
        }
    }

    @Override
    public void steer(SnakeProto.Direction direction) {
        playerSnake.setNextDirection(direction);
    }

    private InetSocketAddress getDeputyAddress() {
        AtomicReference<InetSocketAddress> deputyAddr = new AtomicReference<>(null);
        players.forEach((addr, player) -> {
            if (player.getRole() == SnakeProto.NodeRole.DEPUTY) {
                deputyAddr.set(addr);
            }
        });
        return deputyAddr.get();
    }

    private void sendRoleChangeToDeputy() {
        InetSocketAddress deputyAddr = getDeputyAddress();

        if (deputyAddr == null) {
            return;
        }
        try {
            sendRoleChangeMessage(deputyAddr, SnakeProto.NodeRole.MASTER);
        } catch (IOException e) {
            game.displayErrorAndStopGame(e);
        }
    }

    @Override
    public void changeToViewer() {
        stop();
        if (players.size() == 1) {
            return;
        }
        InetSocketAddress deputyAddr = getDeputyAddress();
        if (deputyAddr == null) {
            return;
        }
        game.setMasterToViewer(true);
        game.joinGame(gamePanel, name, deputyAddr, SnakeProto.NodeRole.VIEWER, gameConfig.build());
    }

    @Override
    public void start() {
        game.displayGameConfig(gameConfig.build(), name);
        sendAnnounce();
        timer.scheduleAtFixedRate(() -> {
            SnakeProto.GameMessage stateMessage = makeStateMessage();
            updatePanelState(stateMessage.getState().getState());
            sendStateMessages(stateMessage);
            incrementStateOrder();
            recvMessages();
            checkLastMessageTime();
            updateState();
            joinNewPlayers();
            setChanged();
            notifyObservers();
        }, 0, gameConfig.getStateDelayMs(), TimeUnit.MILLISECONDS);
    }
}
