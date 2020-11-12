package controller;

import model.Snake;
import proto.SnakeProto;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class Controller implements KeyListener {
    private final Snake snake;

    public Controller(Snake snake) {
        this.snake = snake;
    }

    @Override
    public void keyTyped(KeyEvent keyEvent) {
        int id = keyEvent.getKeyCode();

        switch (id) {
            case (KeyEvent.VK_W): {
                snake.setDirection(SnakeProto.Direction.UP);
                break;
            }
            case (KeyEvent.VK_S): {
                snake.setDirection(SnakeProto.Direction.DOWN);
                break;
            }
            case (KeyEvent.VK_D): {
                snake.setDirection(SnakeProto.Direction.RIGHT);
                break;
            }
            case (KeyEvent.VK_A): {
                snake.setDirection(SnakeProto.Direction.LEFT);
                break;
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent keyEvent) {

    }

    @Override
    public void keyReleased(KeyEvent keyEvent) {

    }
}
