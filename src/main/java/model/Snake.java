package model;

import proto.SnakeProto;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private final SnakeProto.GameState.Snake.Builder snake;
    private List<SnakeProto.GameState.Coord> unpackedCoords;
    private List<SnakeProto.GameState.Coord> packedCoords;
    private SnakeProto.Direction nextDirection;
    private int gameWidth = 100;
    private int gameHeight = 100;

    /*public Snake(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }*/

    public Snake() {
        packedCoords = new ArrayList<>();
        unpackedCoords = new ArrayList<>();

        unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(5).setY(1).build());
        unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(5).setY(2).build());
        unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(5).setY(3).build());
        snake = SnakeProto.GameState.Snake.newBuilder()
                .addAllPoints(unpackedCoords)
                .setHeadDirection(SnakeProto.Direction.UP);
        packCoords();
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

    public List<SnakeProto.GameState.Coord> getUnpackedCoords() {
        return unpackedCoords;
    }

    private void packCoords() {
        List<SnakeProto.GameState.Coord> coords = getCoords();
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

    private void unpackCoords() {
        SnakeProto.GameState.Coord headCoord = packedCoords.get(0);

        unpackedCoords = new ArrayList<>();
        unpackedCoords.add(headCoord);

        int initialX = headCoord.getX();
        int initialY = headCoord.getY();

        for (int i = 1; i < packedCoords.size(); i++) {
            if (packedCoords.get(i).getX() > 0) {
                for (int j = 1; j <= packedCoords.get(i).getX(); j++) {
                    initialX = (initialX + 1) % gameWidth;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
            if (packedCoords.get(i).getX() < 0) {
                for (int j = 1; j <= Math.abs(packedCoords.get(i).getX()); j++) {
                    initialX = (gameWidth + (initialX - 1)) % gameWidth;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
            if (packedCoords.get(i).getY() > 0) {
                for (int j = 1; j <= packedCoords.get(i).getY(); j++) {
                    initialY = (initialY + 1) % gameHeight;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
            if (packedCoords.get(i).getY() < 0) {
                for (int j = 1; j <= Math.abs(packedCoords.get(i).getY()); j++) {
                    initialY = (gameHeight + (initialY - 1)) % gameHeight;
                    unpackedCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(initialX)
                            .setY(initialY).build());
                }
            }
        }
    }

    public void moveForward() {
        unpackCoords();
        List<SnakeProto.GameState.Coord> oldCoords = unpackedCoords;
        List<SnakeProto.GameState.Coord> newCoords = new ArrayList<>();
        SnakeProto.GameState.Coord oldHeadCoord = oldCoords.get(0);
        setDirection(nextDirection);
        switch (snake.getHeadDirection()) {
            case UP: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX(oldHeadCoord.getX())
                        .setY((gameHeight + (oldHeadCoord.getY() - 1)) % gameHeight).build());
                break;
            }
            case DOWN: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX(oldHeadCoord.getX())
                        .setY((oldHeadCoord.getY() + 1) % gameHeight).build());
                break;
            }
            case RIGHT: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX((oldHeadCoord.getX() + 1) % gameWidth)
                        .setY(oldHeadCoord.getY()).build());
                break;
            }
            case LEFT: {
                newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                        .setX((gameWidth + (oldHeadCoord.getX() - 1)) % gameWidth)
                        .setY(oldHeadCoord.getY()).build());
                break;
            }
        }

        for (int i = 1; i < oldCoords.size(); i++) {
            newCoords.add(oldCoords.get(i - 1));
        }

        snake.clearPoints();
        snake.addAllPoints(newCoords);
        packCoords();
    }
}
