package model;

import proto.SnakeProto;

import java.util.*;

public class Game extends Observable {
    Snake snake;
    Food food;
    private int gameWidth = 100;
    private int gameHeight = 100;

    public Game(Snake snake, Food food, Observer o) {
        this.snake = snake;
        this.food = food;
        food.setField(gameWidth, gameHeight);
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
