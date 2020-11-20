package model;

import proto.SnakeProto;
import view.MainForm;
import view.SettingsForm;

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
    private final SnakeProto.GameConfig.Builder gameConfig = SnakeProto.GameConfig.newBuilder();
    private final SnakeProto.GameMessage.JoinMsg.Builder playerName = SnakeProto.GameMessage.JoinMsg.newBuilder();

    public void addGame(DatagramPacket packet) {
        gamesCounter++;
        gamesMap.putIfAbsent(gamesCounter, (InetSocketAddress) packet.getSocketAddress());
        messageParser.setPacket(packet);
        mainForm.setNewGame(messageParser.getHostName(), gamesCounter,
                messageParser.getSize(), messageParser.getFood());
    }

    public App() {
        Snake snake = new Snake();
        SettingsForm settingsForm = new SettingsForm();
        mainForm = new MainForm(settingsForm);
        settingsForm.setGameConfig(gameConfig);
        settingsForm.setPlayerName(playerName);
        mainForm.getGamePanel().setSnake(snake);
        mainForm.getGamePanel().setKeyBindings();
        Game game = new Game(snake, (Observer) mainForm.getGamePanel());
        game.start(100);
    }
}
