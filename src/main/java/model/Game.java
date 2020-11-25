package model;

import proto.SnakeProto;

import java.util.*;

public class Game extends Observable {
    Snake snake;
    Food food;
    private int gameWidth;
    private int gameHeight;

    public Game(Snake snake, Food food, Observer o, int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.snake = snake;
        this.food = food;
        addObserver(o);
    }

    private void snakesUpdate() {
        snake.moveForward(food);
        List<SnakeProto.GameState.Snake.Builder> snakes = new ArrayList<>();
        snakes.add(snake.getSnake());
        food.updateFood(snakes);
    }

    public void start(int stateDelayMs) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                snakesUpdate();
                setChanged();
                notifyObservers();
            }
        }, 0, stateDelayMs);
    }
}
