package player;

import connection.AckSender;
import connection.MessageResender;
import model.Food;
import model.Game;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Normal extends Observable implements Player {
    Timer timer = new Timer();
    private Game game;
    private SnakeProto.NodeRole role;
    private int currentStateOrder = 0;
    private Long msgSeq = 1L;
    private final DatagramSocket socket;
    private final int playerId;
    private int masterId;

    private Long lastMessageTime;
    private final MessageResender messageResender;
    private Snake playerSnake;
    private InetSocketAddress masterAddress;
    private final SnakeProto.GameConfig gameConfig;
    private final GamePanel gamePanel;
    private SnakeProto.GameMessage state = null;


    public Normal(Game game, GamePanel gamePanel, int playerId,
                  int masterId,
                  MessageResender messageResender,
                  DatagramSocket socket,
                  InetSocketAddress masterAddress,
                  SnakeProto.GameConfig gameConfig,
                  SnakeProto.NodeRole role) {
        lastMessageTime = Instant.now().toEpochMilli();
        this.game = game;
        this.gamePanel = gamePanel;
        this.playerId = playerId;
        this.masterId = masterId;
        this.messageResender = messageResender;
        this.socket = socket;
        this.masterAddress = masterAddress;
        this.gameConfig = gameConfig;
        this.role = role;
    }

    private void setRole(SnakeProto.NodeRole receiverRole) {
        this.role = receiverRole;
    }

    private void incrementMsgSeq() {
        msgSeq++;
    }

    private boolean initGamePanel() {
        if (state == null) {
            return false;
        }
        state.getState().getState().getSnakesList().forEach(snake -> {
            if (snake.getPlayerId() == playerId) {
                playerSnake = new Snake(SnakeProto.GameState.Snake.newBuilder(snake), gameConfig.getWidth(),
                        gameConfig.getHeight());
                playerSnake.setNextDirection(snake.getHeadDirection());
            }
        });
        gamePanel.setGameSize(gameConfig.getWidth(), gameConfig.getHeight());
        gamePanel.setPlayer(this);
        gamePanel.setKeyBindings();
        addObserver((Observer) gamePanel);
        gamePanel.setPlaying(true);
        return true;
    }

    private SnakeProto.GameMessage makeSteerMsg() {
        SnakeProto.GameMessage.SteerMsg.Builder steerMsg = SnakeProto.GameMessage.SteerMsg.newBuilder();
        steerMsg.setDirection(playerSnake.getNextDirection());

        SnakeProto.GameMessage.Builder gameMessage = SnakeProto.GameMessage.newBuilder();
        gameMessage.setSteer(steerMsg.build())
                .setMsgSeq(msgSeq);
        return gameMessage.build();
    }

    private void updateLastMessageTime() {
        lastMessageTime = Instant.now().toEpochMilli();
    }


    private void sendSteerMessage() throws IOException {
        SnakeProto.GameMessage message = makeSteerMsg();
        byte[] buf = message.toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, masterAddress.getAddress(), masterAddress.getPort());
        socket.send(packet);

        messageResender.setMessagesToResend(masterAddress, msgSeq, message);
        updateLastMessageTime();
        incrementMsgSeq();
    }

    private void updateState() {
        if (state.getState().getState().getStateOrder() > currentStateOrder) {
            currentStateOrder = state.getState().getState().getStateOrder();
        } else {
            return;
        }
        List<Snake> snakes = new ArrayList<>();
        AtomicBoolean dead = new AtomicBoolean(true);
        state.getState().getState().getSnakesList().forEach(snake -> {
            Snake s = new Snake(SnakeProto.GameState.Snake.newBuilder(snake),
                    gameConfig.getWidth(), gameConfig.getHeight());
            if (s.getPlayerId() == playerId) {
                dead.set(false);
            }
            s.unpackCoords();
            s.setCoords(s.getUnpackedCoords());
            snakes.add(s);
        });
        if (dead.get()) {
            setRole(SnakeProto.NodeRole.VIEWER);
        }
        Food food = new Food(state.getState().getState().getFoodsList());
        gamePanel.setFood(food);
        gamePanel.setPlayers(state.getState().getState().getPlayers().getPlayersList());
        gamePanel.setSnakes(snakes);
    }

    private void sendAck(InetSocketAddress socketAddress, Long msgSeq) throws IOException {
        AckSender.sendAck(socketAddress, msgSeq, socket, playerId, masterId);
    }

    private void updateAcks(InetSocketAddress socketAddress, Long msgSeq) {
        messageResender.removeMessage(socketAddress, msgSeq);
    }

    private void sendPing() throws IOException {
        if (Instant.now().toEpochMilli() - lastMessageTime > gameConfig.getPingDelayMs()) {
            SnakeProto.GameMessage.PingMsg.Builder pingMsg = SnakeProto.GameMessage.PingMsg.newBuilder();
            SnakeProto.GameMessage.Builder gameMessage = SnakeProto.GameMessage.newBuilder();
            gameMessage.setPing(pingMsg).setMsgSeq(msgSeq);
            SnakeProto.GameMessage message = gameMessage.build();
            byte[] buf = message.toByteArray();
            DatagramPacket packet =
                    new DatagramPacket(buf, buf.length, masterAddress.getAddress(), masterAddress.getPort());
            socket.send(packet);
            messageResender.setMessagesToResend(masterAddress, msgSeq, message);
            updateLastMessageTime();
            incrementMsgSeq();
        }
    }

    private boolean findNewMaster() {
        AtomicBoolean found = new AtomicBoolean(false);
        state.getState().getState().getPlayers().getPlayersList().forEach(player -> {
            if (player.getRole() == SnakeProto.NodeRole.DEPUTY) {
                found.set(true);
                masterId = player.getId();
                masterAddress = new InetSocketAddress(player.getIpAddress(), player.getPort());
            }
        });
        return found.get();
    }

    private void sendRoleChangeMessage() throws IOException {
        SnakeProto.GameMessage.RoleChangeMsg.Builder roleChangeMsg =
                SnakeProto.GameMessage.RoleChangeMsg.newBuilder();
        roleChangeMsg
                .setReceiverRole(SnakeProto.NodeRole.MASTER)
                .setSenderRole(role);

        SnakeProto.GameMessage.Builder gameMessage = SnakeProto.GameMessage.newBuilder();
        gameMessage.setRoleChange(roleChangeMsg.build())
                .setMsgSeq(msgSeq)
                .setSenderId(playerId)
                .setReceiverId(masterId);

        SnakeProto.GameMessage message = gameMessage.build();
        byte[] buf = message.toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, masterAddress.getAddress(), masterAddress.getPort());
        socket.send(packet);
        messageResender.setMessagesToResend(masterAddress, msgSeq, message);
        incrementMsgSeq();
    }

    private void changeMaster() {
        if (role == SnakeProto.NodeRole.DEPUTY) {
            if (state != null) {
                timer.cancel();
                game.changeDeputyToMaster(state, playerId, gamePanel, gameConfig, socket, messageResender);
            } else {
                stop();
            }
        } else {
            if (!findNewMaster()) {
                stop();
            }
        }
    }

    private void setNewMaster(InetSocketAddress address, int masterId) {
        this.masterId = masterId;
        this.masterAddress = address;
    }

    private void recvMessages() {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            socket.setSoTimeout(gameConfig.getNodeTimeoutMs());
            socket.receive(packet);
            byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
            SnakeProto.GameMessage gameMsg = SnakeProto.GameMessage.parseFrom(msg);
            if (gameMsg.hasAck()) {
                updateAcks((InetSocketAddress) packet.getSocketAddress(), gameMsg.getMsgSeq());
                return;
            } else if (gameMsg.hasState()) {
                state = SnakeProto.GameMessage.parseFrom(msg);
            } else if (gameMsg.hasRoleChange()) {
                if ((gameMsg.getRoleChange().hasSenderRole())
                        && (gameMsg.getRoleChange().getSenderRole() == SnakeProto.NodeRole.MASTER)) {
                    setNewMaster((InetSocketAddress) packet.getSocketAddress(), gameMsg.getSenderId());
                }
                setRole(gameMsg.getRoleChange().getReceiverRole());
            }
            sendAck((InetSocketAddress) packet.getSocketAddress(), gameMsg.getMsgSeq());
        } catch (SocketTimeoutException e) {
            messageResender.removeReceiver(masterAddress);
            changeMaster();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void start() {
        do {
            recvMessages();
        } while (!initGamePanel());
        updateState();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                setChanged();
                notifyObservers();
                recvMessages();
                updateState();
                try {
                    sendPing();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 1);
    }

    @Override
    public void stop() {
        messageResender.interrupt();
    }

    @Override
    public void steer(SnakeProto.Direction direction) {
        try {
            if (role != SnakeProto.NodeRole.VIEWER) {
                playerSnake.setNextDirection(direction);
                sendSteerMessage();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void changeToViewer() {
        setRole(SnakeProto.NodeRole.VIEWER);
        try {
            sendRoleChangeMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
