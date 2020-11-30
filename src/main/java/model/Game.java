package model;

import com.google.protobuf.InvalidProtocolBufferException;
import player.Master;
import player.Normal;
import player.Player;
import proto.SnakeProto;
import view.GamePanel;
import view.JoinGameException;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Game {
    private DatagramSocket socket;
    private Player player;
    private App app;

    public Game(App app) {
        this.app = app;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println(e.getMessage());
        }
    }

    private JFrame showConnectionPanel() {
        JFrame frame = new JFrame("Game connection");
        frame.setBounds(0, 0, 200, 100);
        frame.setUndecorated(false);
        frame.add(new JLabel("Waiting for an answer..."));
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(dim.width / 2 - frame.getSize().width / 2,
                dim.height / 2 - frame.getSize().height / 2);
        frame.setVisible(true);
        return frame;
    }

    public void joinGame(String name, int hostId) throws JoinGameException {
        InetSocketAddress host = app.getHost(hostId);
        SnakeProto.GameMessage.JoinMsg.Builder joinMsg = SnakeProto.GameMessage.JoinMsg.newBuilder();
        joinMsg.setName(name);
        byte[] buf = SnakeProto.GameMessage.newBuilder().setJoin(joinMsg)
                .setMsgSeq(1)
                .build().toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, host.getAddress(), host.getPort());

        JFrame connectionFrame = showConnectionPanel();
        try {
            socket.send(packet);
            socket.setSoTimeout(11_000);
            buf = new byte[128];
            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            throw new JoinGameException("Didn't get an answer from " + host.getAddress().toString() + " "
                    + host.getPort());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            connectionFrame.dispose();
        }
        byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        try {
            SnakeProto.GameMessage gameMsg = SnakeProto.GameMessage.parseFrom(msg);
            if (gameMsg.hasJoin()) {
                throw new JoinGameException("You cannot join your own game!");
            } else if (gameMsg.hasError()) {
                throw new JoinGameException(gameMsg.getError().getErrorMessage());
            } else if (gameMsg.hasAck()) {
                int id = gameMsg.getReceiverId();
                if (player != null) {
                    player.stop();
                }
                player = new Normal(id);
                player.start();
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    public void startNewGame(GamePanel gamePanel, SnakeProto.GameConfig.Builder settings, String name) {
        if (player != null) {
            player.stop();
        }
        player = new Master(gamePanel, settings, name, socket);
        player.start();
    }
}
