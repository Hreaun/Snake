package view;

import model.Food;
import model.Snake;
import proto.SnakeProto;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GamePanel extends JPanel implements Observer {
    private final Color[] colors = {Color.WHITE, Color.BLUE, Color.CYAN, Color.GREEN,
            Color.MAGENTA, Color.ORANGE, Color.PINK, Color.RED};

    private int gameWidth = 0;
    private int gameHeight = 0;
    private Snake playerSnake;
    private List<Snake> snakes;
    private List<SnakeProto.GamePlayer> players;
    private Food food;
    private boolean isPlaying = false;

    public void setGameSize(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public void setPlayerSnake(Snake playerSnake) {
        this.playerSnake = playerSnake;
    }

    public void setSnakes(List<Snake> snakes) {
        this.snakes = snakes;
    }

    public void setPlayers(List<SnakeProto.GamePlayer> players) {
        this.players = players;
    }

    public void setKeyBindings() {
        Map<String, SnakeProto.Direction> keys = Stream.of(
                new AbstractMap.SimpleEntry<>("W", SnakeProto.Direction.UP),
                new AbstractMap.SimpleEntry<>("A", SnakeProto.Direction.LEFT),
                new AbstractMap.SimpleEntry<>("S", SnakeProto.Direction.DOWN),
                new AbstractMap.SimpleEntry<>("D", SnakeProto.Direction.RIGHT))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        keys.forEach((k, v) -> {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(k), k);
            getActionMap().put(k, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    playerSnake.setNextDirection(v);
                }
            });
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        double k = Math.min((double) this.getHeight(),
                this.getWidth()) / Math.max((double) gameHeight, gameWidth);
        AffineTransform Scale = AffineTransform.getScaleInstance(k, k);
        Graphics2D g2 = (Graphics2D) g;
        g2.setTransform(Scale);
        super.paintComponent(g2);
        if (isPlaying) {
            render((Graphics2D) g);
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        this.repaint();
    }

    public void setFood(Food food) {
        this.food = food;
    }

    void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, gameWidth, gameHeight);

        snakes.forEach(snake -> {
            g.setColor(colors[snake.getPlayerId() % colors.length]);
            snake.getCoords().forEach(v -> {
                g.fillRect(v.getX(), v.getY(), 1, 1);
            });
        });
        g.setColor(Color.YELLOW);
        food.getFoods().forEach(v -> g.fillRect(v.getX(), v.getY(), 1, 1));
    }
}
