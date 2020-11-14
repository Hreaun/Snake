package model;

import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

public class Game extends Observable {
    Snake snake;

    public Game(Snake snake, Observer o) {
        this.snake = snake;
        addObserver(o);
    }

    private void snakesUpdate() {
        snake.moveForward();
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
