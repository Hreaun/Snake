package model;

import proto.SnakeProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Snake {
    private boolean alive = true;
    private final SnakeProto.GameState.Snake.Builder snake;
    private List<SnakeProto.GameState.Coord> packedCoords;
    private List<SnakeProto.GameState.Coord> unpackedCoords;
    private SnakeProto.Direction nextDirection;
    private boolean ateFood = false;
    private int gameWidth;
    private int gameHeight;

    public Snake(int gameWidth, int gameHeight, int playerId,
                 List<SnakeProto.GameState.Coord> initialCoords,
                 SnakeProto.Direction headDirection) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;

        snake = SnakeProto.GameState.Snake.newBuilder();
        snake.setPlayerId(playerId)
                .addAllPoints(initialCoords)
                .setState(SnakeProto.GameState.Snake.SnakeState.ALIVE)
                .setHeadDirection(headDirection);
    }

    public Snake(SnakeProto.GameState.Snake.Builder snake, int gameWidth, int gameHeight) {
        this.snake = snake;
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
        packedCoords = snake.getPointsList();
    }

    private void checkFoodCollision(Food food, SnakeProto.GameState.Coord head) {
        food.getFoods().forEach(f -> {
            if ((head.getX() == f.getX()) && head.getY() == f.getY()) {
                ateFood = true;
            }
        });
        if (ateFood) {
            food.getFoods().remove(head);
        }
    }

    public boolean isAlive() {
        return alive;
    }

    public void kill() {
        this.alive = false;
    }

    public void makeZombie() {
        snake.setState(SnakeProto.GameState.Snake.SnakeState.ZOMBIE);
    }

    public SnakeProto.GameState.Snake.SnakeState getState() {
        return snake.getState();
    }

    public void toFood(Food food, float probability) {
        if (!alive) {
            Random random = new Random();
            getCoords().forEach(point -> {
                if (random.nextFloat() < probability) {
                    food.add(point);
                }
            });
        }
    }

    public void setNextDirection(SnakeProto.Direction nextDirection) {
        this.nextDirection = nextDirection;
    }

    public SnakeProto.Direction getDirection() {
        return snake.getHeadDirection();
    }

    public SnakeProto.Direction getNextDirection() {
        return nextDirection;
    }

    public int getPlayerId() {
        return snake.getPlayerId();
    }

    private void setDirection(SnakeProto.Direction direction) {
        switch (snake.getHeadDirection()) {
            case DOWN:
            case UP: {
                if (direction == SnakeProto.Direction.LEFT || direction == SnakeProto.Direction.RIGHT) {
                    break;
                }
                return;
            }
            case RIGHT:
            case LEFT: {
                if (direction == SnakeProto.Direction.UP || direction == SnakeProto.Direction.DOWN) {
                    break;
                }
                return;
            }
        }
        snake.setHeadDirection(direction);
    }

    public void setCoords(List<SnakeProto.GameState.Coord> coords) {
        snake.clearPoints();
        snake.addAllPoints(coords);
    }

    public List<SnakeProto.GameState.Coord> getCoords() {
        return snake.getPointsList();
    }

    public List<SnakeProto.GameState.Coord> getUnpackedCoords() {
        return unpackedCoords;
    }

    public List<SnakeProto.GameState.Coord> getPackedCoords() {
        return packedCoords;
    }

    public void packCoords() {
        List<SnakeProto.GameState.Coord> coords = snake.getPointsList();
        SnakeProto.GameState.Coord headCoord = coords.get(0);

        packedCoords = new ArrayList<>();
        packedCoords.add(headCoord);

        int x = 0;
        int y = 0;

        for (int i = 0; i < coords.size() - 1; i++) {
            if (coords.get(i + 1).getX() != coords.get(i).getX()) {
                if (y != 0) {
                    packedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(0)
                            .setY(y).build());
                    y = 0;
                }
                if ((coords.get(i + 1).getX() == gameWidth - 1) && (coords.get(i).getX() == 0)) {
                    x -= 1;
                } else if ((coords.get(i + 1).getX() == 0) && (coords.get(i).getX() == gameWidth - 1)) {
                    x += 1;
                } else {
                    x += coords.get(i + 1).getX() - coords.get(i).getX();
                }
            }
            if (coords.get(i + 1).getY() != coords.get(i).getY()) {
                if (x != 0) {
                    packedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(x)
                            .setY(0).build());
                    x = 0;
                }
                if ((coords.get(i + 1).getY() == gameHeight - 1) && (coords.get(i).getY() == 0)) {
                    y -= 1;
                } else if ((coords.get(i + 1).getY() == 0) && (coords.get(i).getY() == gameHeight - 1)) {
                    y += 1;
                } else {
                    y += coords.get(i + 1).getY() - coords.get(i).getY();
                }
            }
        }
        packedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                .setX(x)
                .setY(y).build());
    }

    public void unpackCoords() {
        List<SnakeProto.GameState.Coord> coords = snake.getPointsList();
        SnakeProto.GameState.Coord headCoord = coords.get(0);

        List<SnakeProto.GameState.Coord> unpackedCoords = new ArrayList<>();
        unpackedCoords.add(headCoord);

        int initialX = headCoord.getX();
        int initialY = headCoord.getY();

        for (int i = 1; i < coords.size(); i++) {
            if (coords.get(i).getX() > 0) {
                for (int j = 1; j <= coords.get(i).getX(); j++) {
                    initialX = (initialX + 1) % gameWidth;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
            if (coords.get(i).getX() < 0) {
                for (int j = 1; j <= Math.abs(coords.get(i).getX()); j++) {
                    initialX = (gameWidth + (initialX - 1)) % gameWidth;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
            if (coords.get(i).getY() > 0) {
                for (int j = 1; j <= coords.get(i).getY(); j++) {
                    initialY = (initialY + 1) % gameHeight;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
            if (coords.get(i).getY() < 0) {
                for (int j = 1; j <= Math.abs(coords.get(i).getY()); j++) {
                    initialY = (gameHeight + (initialY - 1)) % gameHeight;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
        }
        this.unpackedCoords = unpackedCoords;
    }

    public boolean ateFood() {
        return ateFood;
    }

    public void moveForward(Food food) {
        ateFood = false;
        List<SnakeProto.GameState.Coord> oldCoords = snake.getPointsList();
        List<SnakeProto.GameState.Coord> newCoords = new ArrayList<>();
        SnakeProto.GameState.Coord oldHeadCoord = oldCoords.get(0);
        setDirection(nextDirection);
        SnakeProto.GameState.Coord nextHeadCoord = null;
        switch (snake.getHeadDirection()) {
            case UP: {
                nextHeadCoord = SnakeProto.GameState.Coord.newBuilder()
                        .setX(oldHeadCoord.getX())
                        .setY((gameHeight + (oldHeadCoord.getY() - 1)) % gameHeight).build();
                break;
            }
            case DOWN: {
                nextHeadCoord = SnakeProto.GameState.Coord.newBuilder()
                        .setX(oldHeadCoord.getX())
                        .setY((oldHeadCoord.getY() + 1) % gameHeight).build();
                break;
            }
            case RIGHT: {

                nextHeadCoord = SnakeProto.GameState.Coord.newBuilder()
                        .setX((oldHeadCoord.getX() + 1) % gameWidth)
                        .setY(oldHeadCoord.getY()).build();
                break;
            }
            case LEFT: {
                nextHeadCoord = SnakeProto.GameState.Coord.newBuilder()
                        .setX((gameWidth + (oldHeadCoord.getX() - 1)) % gameWidth)
                        .setY(oldHeadCoord.getY()).build();
                break;
            }
        }
        checkFoodCollision(food, nextHeadCoord);
        newCoords.add(nextHeadCoord);
        if (ateFood) {
            newCoords.add(oldHeadCoord);
            for (int i = 1; i < oldCoords.size(); i++) {
                newCoords.add(oldCoords.get(i));
            }
        } else {
            for (int i = 1; i < oldCoords.size(); i++) {
                newCoords.add(oldCoords.get(i - 1));
            }
        }

        snake.clearPoints();
        snake.addAllPoints(newCoords);
    }
}
