package player;

import model.Food;
import model.Snake;
import proto.SnakeProto;
import view.GamePanel;

import java.net.DatagramSocket;
import java.util.*;

public class Master extends Observable implements Player {
    private DatagramSocket socket;
    private SnakeProto.GameConfig.Builder gameConfig;
    Snake snake;
    Food food;
    int stateDelayMs;
    private int gameWidth;
    private int gameHeight;

    public Master(GamePanel gamePanel, SnakeProto.GameConfig.Builder settings) {
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
        // Узел с ролью MASTER рассылает сообщения
        // AnnouncementMsg с интервалом в 1 секунду на multicast-адрес 239.192.0.4, порт 9192.
    }

    @Override
    public void start() {
        Timer timer = new Timer();
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

        /*try {
            socket.setSoTimeout(gameConfig.getStateDelayMs());
            //socket.receive();
        } catch (SocketException e) {
            e.printStackTrace();
        }*/
    }
}
