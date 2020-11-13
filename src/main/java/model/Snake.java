package model;

import proto.SnakeProto;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    SnakeProto.GameState.Snake.Builder snake;

    public Snake() {
        ArrayList<SnakeProto.GameState.Coord> initialCoords = new ArrayList<>();
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(50).setY(50).build());
        initialCoords.add(SnakeProto.GameState.Coord.newBuilder().setX(49).setY(50).build());
        snake = SnakeProto.GameState.Snake.newBuilder().addAllPoints(initialCoords);
    }

    public void setDirection(SnakeProto.Direction direction) {
        snake.setHeadDirection(direction);
    }

    public void setCoords(List<SnakeProto.GameState.Coord> coords) {
        snake.addAllPoints(coords);
    }

    public List<SnakeProto.GameState.Coord> getCoords () {
        return snake.getPointsList();
    }
}
