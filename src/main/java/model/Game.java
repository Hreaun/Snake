package model;

import player.Master;
import proto.SnakeProto;
import view.GamePanel;

public class Game {
    public static void startNewGame(GamePanel gamePanel, SnakeProto.GameConfig.Builder settings) {
        Master master = new Master(gamePanel, settings);
        master.start();
    }
}
