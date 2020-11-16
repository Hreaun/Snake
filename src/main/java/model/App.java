package model;

import view.MainForm;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Observer;

public class App {
    private int gamesCounter = 0;
    private final Map<Integer, InetSocketAddress> gamesMap = new HashMap<>();
    private final MainForm mainForm;
    private final MessageParser messageParser = new MessageParser();

    public void addGame(DatagramPacket packet) {
        gamesCounter++;
        gamesMap.putIfAbsent(gamesCounter, (InetSocketAddress) packet.getSocketAddress());
        messageParser.setPacket(packet);
        mainForm.setNewGame(messageParser.getHostName(), gamesCounter,
                messageParser.getSize(), messageParser.getFood());
    }

    public App() {
        Snake snake = new Snake();
        mainForm = new MainForm();
        mainForm.getGamePanel().setSnake(snake);
        mainForm.getGamePanel().setKeyBindings();
        Game game = new Game(snake, (Observer) mainForm.getGamePanel());
        game.start(100);
    }
}
