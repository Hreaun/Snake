package model;

import connection.MulticastListener;
import proto.SnakeProto;
import view.MainForm;
import view.SettingsForm;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class App {
    private int gamesCounter = 0;
    private final Map<Integer, InetSocketAddress> gamesMap = new HashMap<>();
    private final Map<Integer, SnakeProto.GameConfig> gameConfigMap = new HashMap<>();
    private final MainForm mainForm;
    private final MessageParser messageParser = new MessageParser();
    private final SnakeProto.GameConfig.Builder gameConfig = SnakeProto.GameConfig.newBuilder();
    private final SnakeProto.GameMessage.JoinMsg.Builder playerName = SnakeProto.GameMessage.JoinMsg.newBuilder();


    public void addGame(DatagramPacket packet) {
        int gameId;
        if (!gamesMap.containsValue(packet.getSocketAddress())) {
            gameId = ++gamesCounter;
            gamesMap.put(gameId, (InetSocketAddress) packet.getSocketAddress());
        } else {
            gameId = gamesMap
                    .entrySet()
                    .stream()
                    .filter(entry -> packet.getSocketAddress().equals(entry.getValue()))
                    .map(Map.Entry::getKey).findFirst().get();
        }
        messageParser.setPacket(packet);
        gameConfigMap.put(gameId, messageParser.getConfig());
        mainForm.setNewOrUpdateGame(messageParser.getHostName(), gameId,
                messageParser.getSize(), messageParser.getFood());
    }

    public InetSocketAddress getHost(int hostId) {
        return gamesMap.get(hostId);
    }

    public SnakeProto.GameConfig getGameConfig(int hostId) {
        return gameConfigMap.get(hostId);
    }

    public App() {
        SettingsForm settingsForm = new SettingsForm();
        settingsForm.setGameConfig(gameConfig);
        settingsForm.setPlayerName(playerName);
        MulticastListener multicastListener = new MulticastListener(this);
        mainForm = new MainForm(settingsForm, new Game(this));
        multicastListener.start();
    }
}
