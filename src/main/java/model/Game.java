package model;

import player.Master;
import proto.SnakeProto;
import view.GamePanel;

import java.net.DatagramSocket;
import java.net.SocketException;

public class Game {
    private DatagramSocket socket;
    private Master master;

    {
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println(e.getMessage());
        }
    }


    public void startNewGame(GamePanel gamePanel, SnakeProto.GameConfig.Builder settings, String name) {
        if (master != null) {
            master.stop();
        }
        master = new Master(gamePanel, settings, name, socket);
        master.start();
    }
}
