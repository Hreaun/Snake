package player;

import proto.SnakeProto;

public interface Player {
    void start();
    void stop();
    void steer(SnakeProto.Direction direction);
    void changeToViewer();
}
