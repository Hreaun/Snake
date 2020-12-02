package player;

import connection.AckSender;
import connection.MessageResender;
import model.Food;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Normal extends Observable implements Player{
    private int currentStateOrder = 0;
    private Long msgSeq = 1L;
    private final DatagramSocket socket;
    private final int id;
    private final MessageResender messageResender;
    private Snake playerSnake;
    private InetSocketAddress masterAddress;
    private final SnakeProto.GameConfig gameConfig;
    private final GamePanel gamePanel;
    private SnakeProto.GameMessage state;


    public Normal(GamePanel gamePanel, int id,
                  MessageResender messageResender,
                  DatagramSocket socket,
                  InetSocketAddress masterAddress,
                  SnakeProto.GameConfig gameConfig) {
        this.gamePanel = gamePanel;
        this.id = id;
        this.messageResender = messageResender;
        this.socket = socket;
        this.masterAddress = masterAddress;
        this.gameConfig = gameConfig;
    }

    private void incrementMsgSeq() {
        msgSeq++;
    }

    private void initGamePanel() {
        state.getState().getState().getSnakesList().forEach(snake -> {
            if (snake.getPlayerId() == id) {
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
    }

    private SnakeProto.GameMessage makeSteerMsg() {
        SnakeProto.GameMessage.SteerMsg.Builder steerMsg = SnakeProto.GameMessage.SteerMsg.newBuilder();
        steerMsg.setDirection(playerSnake.getNextDirection());

        SnakeProto.GameMessage.Builder gameMessage = SnakeProto.GameMessage.newBuilder();
        gameMessage.setSteer(steerMsg.build())
                .setMsgSeq(msgSeq);
        return gameMessage.build();
    }


    private void sendSteerMessage() throws IOException {
        SnakeProto.GameMessage message = makeSteerMsg();
        byte[] buf = message.toByteArray();
        DatagramPacket packet =
                    new DatagramPacket(buf, buf.length, masterAddress.getAddress(), masterAddress.getPort());
        socket.send(packet);
        messageResender.setMessagesToResend(masterAddress, msgSeq, message);
        incrementMsgSeq();
    }

    private void updateState() {
        if (state.getState().getState().getStateOrder() > currentStateOrder) {
            currentStateOrder = state.getState().getState().getStateOrder();
        } else {
            return;
        }
        List<Snake> snakes = new ArrayList<>();
        state.getState().getState().getSnakesList().forEach(snake -> {
            Snake s = new Snake(SnakeProto.GameState.Snake.newBuilder(snake),
                    gameConfig.getWidth(), gameConfig.getHeight());
            s.unpackCoords();
            s.setCoords(s.getUnpackedCoords());
            snakes.add(s);
        });
        Food food = new Food(state.getState().getState().getFoodsList());
        gamePanel.setFood(food);
        gamePanel.setPlayers(state.getState().getState().getPlayers().getPlayersList());
        gamePanel.setSnakes(snakes);
    }

    private void sendAck(InetSocketAddress socketAddress, Long msgSeq) throws IOException {
        AckSender.sendAck(socketAddress, msgSeq, socket);
    }

    private void updateAcks(InetSocketAddress socketAddress, Long msgSeq) {
        messageResender.removeMessage(socketAddress, msgSeq);
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
            }
            sendAck((InetSocketAddress) packet.getSocketAddress(), gameMsg.getMsgSeq());
        } catch (SocketTimeoutException e) {
            System.out.println("timeout");
            //обработка падения мастера
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void start() {
        Timer timer = new Timer();
        recvMessages();
        initGamePanel();
        updateState();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                setChanged();
                notifyObservers();
                recvMessages();
                updateState();
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
            playerSnake.setNextDirection(direction);
            sendSteerMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
