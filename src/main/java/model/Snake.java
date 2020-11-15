package model;

import proto.SnakeProto;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private final SnakeProto.GameState.Snake.Builder snake;
    private SnakeProto.Direction nextDirection;
    private int gameWidth = 100;
    private int gameHeight = 100;

    /*public Snake(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }*/

    public Snake() {
        List<SnakeProto.GameState.Coord> initialCoords = new ArrayList<>();
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(50).setY(50).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(49).setY(50).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(48).setY(50).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(47).setY(99).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(46).setY(99).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(45).setY(99).build());
        snake = SnakeProto.GameState.Snake.newBuilder().addAllPoints(initialCoords);
    }

    public void setNextDirection(SnakeProto.Direction nextDirection) {
        this.nextDirection = nextDirection;
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
        snake.addAllPoints(coords);
    }

    public List<SnakeProto.GameState.Coord> getCoords() {
        return snake.getPointsList();
    }

    public void moveForward() {
        List<SnakeProto.GameState.Coord> oldCoords = getCoords();
        List<SnakeProto.GameState.Coord> newCoords = new ArrayList<>();
        SnakeProto.GameState.Coord headCoord = oldCoords.get(0);
        setDirection(nextDirection);
        switch (snake.getHeadDirection()) {
            case UP: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX(headCoord.getX())
                        .setY((gameHeight + (headCoord.getY() - 1)) % gameHeight).build());
                break;
            }
            case DOWN: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX(headCoord.getX())
                        .setY((headCoord.getY() + 1) % gameHeight).build());
                break;
            }
            case RIGHT: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX((headCoord.getX() + 1) % gameWidth)
                        .setY(headCoord.getY()).build());
                break;
            }
            case LEFT: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX((gameWidth + (headCoord.getX() - 1)) % gameWidth)
                        .setY(headCoord.getY()).build());
                break;
            }
        }
        for (int i = 1; i < oldCoords.size(); i++) {
            newCoords.add(oldCoords.get(i - 1));
        }
        snake.clearPoints();
        snake.addAllPoints(newCoords);
    }
}
