package model;

import proto.SnakeProto;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Food {
    private final List<SnakeProto.GameState.Coord> foods;
    private boolean[][] busyField;
    private int foodStatic;
    private int foodPerPlayer;
    private final Random random = new Random();
    private int gameWidth;
    private int gameHeight;

    public Food(int foodStatic, int foodPerPlayer, int gameWidth, int gameHeight) {
        this.foodStatic = foodStatic;
        this.foodPerPlayer = foodPerPlayer;
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.foods = new ArrayList<>();
    }

    public Food(int foodStatic, int foodPerPlayer, int gameWidth, int gameHeight,
                List<SnakeProto.GameState.Coord> foods){
        this.foodStatic = foodStatic;
        this.foodPerPlayer = foodPerPlayer;
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        this.foods = new ArrayList<>(foods);
    }

    public Food(List<SnakeProto.GameState.Coord> foods) {
        this.foods = foods;
    }

    public List<SnakeProto.GameState.Coord> getFoods() {
        return foods;
    }

    public void addToCheckField(InetSocketAddress[][] field) {
        foods.forEach(point -> {
            field[point.getX()][point.getY()] = new InetSocketAddress(0);
        });
    }

    public void add(SnakeProto.GameState.Coord point) {
        foods.add(point);
    }

    public void updateFood(List<Snake> snakes) {
        if (foods.size() != 0) {
            return;
        }

        busyField = new boolean[gameWidth][gameHeight];

        AtomicInteger busyPointsCounter = new AtomicInteger();
        snakes.forEach(snake -> snake.getCoords().forEach(point -> {
            busyField[point.getX()][point.getY()] = true;
            busyPointsCounter.getAndIncrement();
        }));

        int foodAmount = foodStatic + foodPerPlayer * snakes.size();

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
