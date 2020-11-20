package model;

import proto.SnakeProto;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private final SnakeProto.GameState.Snake.Builder snake;
    private List<SnakeProto.GameState.Coord> coordsToRender;
    private SnakeProto.Direction nextDirection;
    private int gameWidth = 100;
    private int gameHeight = 100;

    /*public Snake(int gameWidth, int gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }*/

    public Snake() {
        coordsToRender = new ArrayList<>();

        List<SnakeProto.GameState.Coord> initialCoords = new ArrayList<>();
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(5).setY(1).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(3).setY(0).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(0).setY(2).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(-4).setY(0).build());
        snake = SnakeProto.GameState.Snake.newBuilder()
                .addAllPoints(initialCoords)
                .setHeadDirection(SnakeProto.Direction.LEFT);
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

    private void updateCoordsToRender() {
        // сделать рендер змейки!!!!!
    }

    public void moveForward() {
        List<SnakeProto.GameState.Coord> oldCoords = getCoords();
        List<SnakeProto.GameState.Coord> newCoords = new ArrayList<>();
        SnakeProto.GameState.Coord oldHeadCoord = oldCoords.get(0);
        SnakeProto.Direction oldDirection = snake.getHeadDirection();
        setDirection(nextDirection); // не использовать nextDirection ниже!
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
        if (oldDirection != snake.getHeadDirection()) {
            newCoords.add(oldHeadCoord);
        }
        for (int i = 1; i < oldCoords.size(); i++) {
            if (oldCoords.get(i).getX() != 0) {
                if ((snake.getHeadDirection() == SnakeProto.Direction.LEFT) && (oldCoords.get(i).getX() > 0)) {
                    if (oldCoords.get(i).getX() - 1 != 0) {
                        newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                                .setX(oldCoords.get(i).getX() - 1)
                                .setY(oldCoords.get(i).getY()).build());
                    }
                } else {
                    newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(oldCoords.get(i).getX() + 1)
                            .setY(oldCoords.get(i).getY()).build());
                }
            } else {
                if ((snake.getHeadDirection() == SnakeProto.Direction.UP) && (oldCoords.get(i).getY() > 0)) {
                    if (oldCoords.get(i).getY() - 1 != 0) {
                        newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                                .setX(oldCoords.get(i).getX())
                                .setY(oldCoords.get(i).getY() - 1).build());
                    }
                } else {
                    newCoords.add(SnakeProto.GameState.Coord.newBuilder()
                            .setX(oldCoords.get(i).getX())
                            .setY(oldCoords.get(i).getY() + 1).build());
                }
            }
        }
        snake.clearPoints();
        snake.addAllPoints(newCoords);
    }
}
