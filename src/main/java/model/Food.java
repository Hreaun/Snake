package model;

import proto.SnakeProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Food {
    private final List<SnakeProto.GameState.Coord> foods;
    private boolean[][] busyField;
    private int foodAmount = 3;
    private final Random random;
    private final int gameWidth;
    private final int gameHeight;

    public Food(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        random = new Random();
        this.foods = new ArrayList<>();
    }

    public List<SnakeProto.GameState.Coord> getFoods() {
        return foods;
    }

    public void updateFood(List<SnakeProto.GameState.Snake.Builder> snakes) {
        if (foods.size() != 0) {
            return;
        }

        busyField = new boolean[gameWidth][gameHeight];

        AtomicInteger busyPointsCounter = new AtomicInteger();
        snakes.forEach(snake -> snake.getPointsList().forEach(point -> {
            busyField[point.getX()][point.getY()] = true;
            busyPointsCounter.getAndIncrement();
        }));

        if ((gameWidth * gameHeight - busyPointsCounter.intValue()) >= foodAmount) {
            for (int i = 0; i < foodAmount; i++) {
                int x;
                int y;
                do {
                    x = random.nextInt(gameWidth);
                    y = random.nextInt(gameHeight);
                } while (busyField[x][y]);
                busyField[x][y] = true;
                foods.add(SnakeProto.GameState.Coord.newBuilder().setX(x).setY(y).build());
            }
        }
    }
}
