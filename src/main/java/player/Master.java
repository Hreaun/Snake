package player;

import model.Food;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

import static java.lang.Thread.sleep;

public class Master extends Observable implements Player {
    private DatagramSocket socket;
    private SnakeProto.GameConfig.Builder gameConfig;
    Thread announceThread;
    Snake snake;
    Food food;
    String name;
    int stateDelayMs;
    private int gameWidth;
    private int gameHeight;

    public Master(GamePanel gamePanel, SnakeProto.GameConfig.Builder settings, String name, DatagramSocket socket) {
        this.socket = socket;
        this.name = name;
        this.gameConfig = settings.clone();
        this.gameWidth = settings.getWidth();
        this.gameHeight = settings.getHeight();
        this.stateDelayMs = settings.getStateDelayMs();
        snake = new Snake(gameWidth, gameHeight);
        food = new Food(settings.getFoodStatic(), (int) settings.getFoodPerPlayer(), gameWidth, gameHeight);
        gamePanel.setGameSize(gameWidth, gameHeight);
        gamePanel.setSnake(snake);
        gamePanel.setFood(food);
        gamePanel.setKeyBindings();
        addObserver((Observer) gamePanel);
        gamePanel.setPlaying(true);
    }

    private boolean snakesUpdate() {
        snake.moveForward(food);
        List<SnakeProto.GameState.Snake.Builder> snakes = new ArrayList<>();
        snakes.add(snake.getSnake());
        food.updateFood(snakes);
        return snake.checkSnakeCollision(snake);
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

    public void stop(){
        announceThread.interrupt();
    }

    @Override
    public void start() {
        Timer timer = new Timer();
        sendAnnounce();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (snakesUpdate()) {
                    timer.cancel();
                }
                setChanged();
                notifyObservers();
            }
        }, 0, stateDelayMs);
    }
}
