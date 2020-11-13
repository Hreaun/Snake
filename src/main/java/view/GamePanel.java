package view;

import model.Snake;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Observable;
import java.util.Observer;

public class GamePanel extends JPanel implements Observer {
    private final int width = 100;
    private final int height = 100;
    Snake snake;

    @Override
    protected void paintComponent(Graphics g) {
        double k = 4;
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
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        snake.getCoords().forEach(v -> g.fillRect(v.getX(), v.getY(), 1, 1));
    }
}
