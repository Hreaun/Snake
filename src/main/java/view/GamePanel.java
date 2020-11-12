package view;

import model.Snake;

import javax.swing.*;
import java.awt.*;
import java.util.Observable;
import java.util.Observer;

public class GamePanel extends JPanel implements Observer {
    private final int width = 400;
    private final int height = 400;
    Snake snake;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
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
        snake.getCoords().forEach(v -> {
            g.fillOval(v.getX(), v.getY(), 10, 10);
        });
    }
}
