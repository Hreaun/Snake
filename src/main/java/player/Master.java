package player;

import com.google.protobuf.InvalidProtocolBufferException;
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
    private final DatagramSocket socket;
    private final SnakeProto.GameConfig.Builder gameConfig;
    private Map<InetSocketAddress, SnakeProto.GameMessage> steerMessages;
    private Map<InetSocketAddress, SnakeProto.GameMessage.JoinMsg> joinMessages;
    private final Map<InetSocketAddress, Long> lastMsgTime = new HashMap<>();
    private final Map<Long, List<InetSocketAddress>> ackMessages = new HashMap<>();
    private final Map<Integer, SnakeProto.GameMessage> sentMessages = new HashMap<>();
    private Thread announceThread;
    private final Map<InetSocketAddress, Snake> snakes = new HashMap<>();
    private final Food food;
    private final String name;

    public Master(GamePanel gamePanel,
                  SnakeProto.GameConfig.Builder settings,
                  String name,
                  DatagramSocket socket) {
        this.socket = socket;
        this.name = name;
        this.gameConfig = settings.clone();
        snakes.put((InetSocketAddress) socket.getLocalSocketAddress(), new Snake(gameConfig.getWidth(), gameConfig.getHeight()));
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

    private void updateState() {
        snakes.forEach((addr, snake) -> {
            if (steerMessages.containsKey(addr)) {
                snake.setNextDirection(steerMessages.get(addr).getSteer().getDirection());
            }
            snake.moveForward(food);
        });
        snakes.forEach((addr, snake) -> {
            snake.checkSnakeCollision(snakes);
        });
        food.updateFood(new ArrayList<>(snakes.values()));
    }

    private void addJoinMsg(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        joinMessages.putIfAbsent(socketAddress, gameMsg.getJoin());
        lastMsgTime.put(socketAddress, Instant.now().toEpochMilli());
    }

    private void ackPing(InetSocketAddress socketAddress) {
        lastMsgTime.put(socketAddress, Instant.now().toEpochMilli());
    }

    private void updateAcks(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        ackMessages.get(gameMsg.getMsgSeq()).remove(socketAddress);
    }

    private void addSteerMsg(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        lastMsgTime.put(socketAddress, Instant.now().toEpochMilli());
        if (steerMessages.containsKey(socketAddress)) {
            if (steerMessages.get(socketAddress).getMsgSeq() >= gameMsg.getMsgSeq()) {
                return;
            }
        }
        steerMessages.put(socketAddress, gameMsg);
    }

    private void changePlayerRole(InetSocketAddress socketAddress, SnakeProto.GameMessage gameMsg) {
        steerMessages.remove(socketAddress);
        lastMsgTime.put(socketAddress, Instant.now().toEpochMilli());
        //смена роли NORMAL -> VIEWER
    }


    private void recvMessages() {
        Instant startTime = Instant.now();
        steerMessages = new HashMap<>();
        joinMessages = new HashMap<>();
        Instant endTime = Instant.now();
        while (Duration.between(startTime, endTime).toMillis() < gameConfig.getStateDelayMs()) {
            byte[] buf = new byte[128];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.setSoTimeout((int) Duration.between(startTime, endTime).toMillis());
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
            try {
                SnakeProto.GameMessage gameMsg = SnakeProto.GameMessage.parseFrom(msg);
                if (gameMsg.hasJoin()) {
                    addJoinMsg((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                } else if (gameMsg.hasPing()) {
                    ackPing((InetSocketAddress) packet.getSocketAddress());
                } else if (gameMsg.hasAck()) {
                    updateAcks((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                } else if (gameMsg.hasSteer()) {
                    addSteerMsg((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                } else if (gameMsg.hasRoleChange()) {
                    changePlayerRole((InetSocketAddress) packet.getSocketAddress(), gameMsg);
                }
            } catch (InvalidProtocolBufferException e) {
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
    }

    @Override
    public void start() {
        Timer timer = new Timer();
        sendAnnounce();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                recvMessages();
                updateState();
                setChanged();
                notifyObservers();
            }
        }, 0, gameConfig.getStateDelayMs());
    }
}
