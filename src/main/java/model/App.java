package model;

import proto.SnakeProto;
import view.MainForm;
import view.SettingsForm;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Observer;

public class App {
    private DatagramSocket socket;
    private int gamesCounter = 0;
    private final Map<Integer, InetSocketAddress> gamesMap = new HashMap<>();
    private MainForm mainForm = null;
    private final MessageParser messageParser = new MessageParser();
    private final SnakeProto.GameConfig.Builder gameConfig = SnakeProto.GameConfig.newBuilder();
    private final SnakeProto.GameMessage.JoinMsg.Builder playerName = SnakeProto.GameMessage.JoinMsg.newBuilder();
    private final int gameWidth = 100;
    private final int gameHeight = 100;


    public void addGame(DatagramPacket packet) {
        gamesCounter++;
        gamesMap.putIfAbsent(gamesCounter, (InetSocketAddress) packet.getSocketAddress());
        messageParser.setPacket(packet);
        mainForm.setNewGame(messageParser.getHostName(), gamesCounter,
                messageParser.getSize(), messageParser.getFood());
    }

    public App() {
        try {
            this.socket = new DatagramSocket();
            Snake snake = new Snake(gameWidth, gameHeight);
            Food food = new Food(gameWidth, gameHeight);
            SettingsForm settingsForm = new SettingsForm();
            mainForm = new MainForm(settingsForm);
            settingsForm.setGameConfig(gameConfig);
            settingsForm.setPlayerName(playerName);
            mainForm.getGamePanel().setGameSize(gameWidth, gameHeight);
            mainForm.getGamePanel().setSnake(snake);
            mainForm.getGamePanel().setFood(food);
            mainForm.getGamePanel().setKeyBindings();
            Game game = new Game(snake, food, (Observer) mainForm.getGamePanel(), gameWidth, gameHeight);
            game.start(100);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
}
