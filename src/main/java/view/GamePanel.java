package view;

import model.Snake;
import proto.SnakeProto;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GamePanel extends JPanel implements Observer {
    private final int gameWidth = 100;
    private final int gameHeight = 100;
    Snake snake;

    /*public GamePanel(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }*/

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
                    snake.setDirection(v);
                }
            });
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        double k = Math.min((double) this.getHeight(), this.getWidth()) / Math.max((double) gameHeight, gameWidth);
        AffineTransform Scale = AffineTransform.getScaleInstance(k, k);
        Graphics2D g2 = (Graphics2D) g;
        g2.setTransform(Scale);
        super.paintComponent(g2);
        render((Graphics2D) g);
    }

    @Override
    public void update(Observable observable, Object o) {
        this.repaint();
    }

    public void setSnake(Snake snake) {
        this.snake = snake;
    }

    void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, gameWidth, gameHeight);
        g.setColor(Color.WHITE);
        snake.getCoords().forEach(v -> g.fillRect(v.getX(), v.getY(), 1, 1));
    }
}
