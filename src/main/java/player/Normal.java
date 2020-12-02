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
        System.out.println(state.hasState());
        state.getState().getState().getSnakesList().forEach(snake -> {
            System.out.println(snake.getPlayerId());
            if (snake.getPlayerId() == id) {
                System.out.println("ggg");
                playerSnake = new Snake(SnakeProto.GameState.Snake.newBuilder(snake), gameConfig.getWidth(),
                        gameConfig.getHeight());
                playerSnake.setNextDirection(snake.getHeadDirection());
            }
        });
        gamePanel.setGameSize(gameConfig.getWidth(), gameConfig.getHeight());
        gamePanel.setPlayerSnake(playerSnake);
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

    private void recvStateMessage() {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            socket.setSoTimeout(gameConfig.getNodeTimeoutMs());
            socket.receive(packet);
            byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
            state = SnakeProto.GameMessage.parseFrom(msg);
            sendAck((InetSocketAddress) packet.getSocketAddress(), state.getMsgSeq());
        } catch (SocketTimeoutException e) {
            //обработка падения мастера
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    @Override
    public void start() {
        Timer timer = new Timer();
        recvStateMessage();
        initGamePanel();
        updateState();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    recvStateMessage();
                    updateState();
                    sendSteerMessage();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                setChanged();
                notifyObservers();
            }
        }, 0, gameConfig.getStateDelayMs());
    }

    @Override
    public void stop() {
        messageResender.interrupt();
    }
}
