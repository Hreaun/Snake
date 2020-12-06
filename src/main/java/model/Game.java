package model;

import com.google.protobuf.InvalidProtocolBufferException;
import connection.MessageResender;
import player.Master;
import player.Normal;
import player.Player;
import proto.SnakeProto;
import view.AppException;
import view.GamePanel;
import view.JoinGameException;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Map;

public class Game {
    private DatagramSocket socket;
    private Player player;
    private SnakeProto.NodeRole role = SnakeProto.NodeRole.NORMAL;
    private boolean masterToViewer = false;
    private final App app;

    public Game(App app) {
        this.app = app;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            displayErrorAndStopGame(e);
        }
    }

    public void displayGameConfig(SnakeProto.GameConfig gameConfig, String hostName) {
        app.displayGameConfig(gameConfig, hostName);
    }

    public void displayScore(Map<SnakeProto.GamePlayer, Integer> players, int playerId) {
        app.displayScore(players, playerId);
    }

    private void makeNewSocket() {
        try {
            socket.close();
            socket = new DatagramSocket();
        } catch (SocketException e) {
            displayErrorAndStopGame(e);
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

    private void connectToGame(GamePanel gamePanel, String name, InetSocketAddress hostAddr, SnakeProto.NodeRole role,
                               SnakeProto.GameConfig gameConfig) throws JoinGameException {
        if (this.role == SnakeProto.NodeRole.MASTER) {
            throw new JoinGameException("You cannot join another game while you are MASTER");
        } else if (masterToViewer) {
            masterToViewer = false;
            makeNewSocket();
        } else if (player != null) {
            player.stop();
            makeNewSocket();
        }
        long msgSeq = 0;
        SnakeProto.GameMessage.JoinMsg.Builder joinMsg = SnakeProto.GameMessage.JoinMsg.newBuilder();
        joinMsg.setName(name);
        if (role == SnakeProto.NodeRole.VIEWER) {
            joinMsg.setOnlyView(true);
        }
        SnakeProto.GameMessage gameMessage = SnakeProto.GameMessage.newBuilder().setJoin(joinMsg)
                .setMsgSeq(msgSeq)
                .build();
        byte[] buf = gameMessage.toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, hostAddr.getAddress(), hostAddr.getPort());

        JFrame connectionFrame = showConnectionPanel();
        MessageResender messageResender = new MessageResender(socket,
                gameConfig.getPingDelayMs());
        try {
            socket.send(packet);
            messageResender.setMessagesToResend(hostAddr, msgSeq, gameMessage);
            messageResender.start();
            socket.setSoTimeout(gameConfig.getNodeTimeoutMs());
            buf = new byte[128];
            packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
        } catch (SocketTimeoutException e) {
            messageResender.interrupt();
            throw new JoinGameException("Didn't get an answer from " + hostAddr.getAddress().toString() + " "
                    + hostAddr.getPort());
        } catch (IOException e) {
            messageResender.interrupt();
            displayErrorAndStopGame(e);
        } finally {
            connectionFrame.dispose();
        }
        byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        try {
            SnakeProto.GameMessage gameMsg = SnakeProto.GameMessage.parseFrom(msg);
            if (gameMsg.hasError()) {
                messageResender.interrupt();
                throw new JoinGameException(gameMsg.getError().getErrorMessage());
            } else if ((gameMsg.hasRoleChange()) || (gameMsg.hasAck())) {
                messageResender.removeMessage(hostAddr, msgSeq);
                player = new Normal(this, gamePanel, gameMsg.getReceiverId(), gameMsg.getSenderId(),
                        messageResender, socket, hostAddr, gameConfig,
                        gameMsg.hasRoleChange() ? SnakeProto.NodeRole.DEPUTY : role);
                this.role = role;
                player.start();
            } else {
                messageResender.interrupt();
                throw new JoinGameException("Something went wrong...");
            }
        } catch (InvalidProtocolBufferException e) {
            messageResender.interrupt();
            displayErrorAndStopGame(e);
        }
    }

    public void joinGame(GamePanel gamePanel, String name, int hostId, SnakeProto.NodeRole role) {
        try {
            connectToGame(gamePanel, name, app.getHost(hostId), role, app.getGameConfig(hostId));
        } catch (JoinGameException e) {
            JOptionPane.showMessageDialog(new JFrame(), e.getMessage(),
                    "Game connection error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void joinGame(GamePanel gamePanel, String name, InetSocketAddress hostAddr, SnakeProto.NodeRole role,
                         SnakeProto.GameConfig gameConfig) {
        try {
            connectToGame(gamePanel, name, hostAddr, role, gameConfig);
        } catch (JoinGameException e) {
            JOptionPane.showMessageDialog(new JFrame(), e.getMessage(),
                    "Game connection error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void changeDeputyToMaster(SnakeProto.GameMessage state, int masterId, GamePanel gamePanel,
                                     SnakeProto.GameConfig settings,
                                     DatagramSocket socket, MessageResender messageResender) {
        player = new Master(this, state, masterId, gamePanel,
                SnakeProto.GameConfig.newBuilder(settings), socket, messageResender);
        this.role = SnakeProto.NodeRole.MASTER;
        player.start();
    }

    public void setMasterToViewer(boolean masterToViewer) {
        this.masterToViewer = masterToViewer;
    }

    public void changeToViewer() {
        if (player != null) {
            this.role = SnakeProto.NodeRole.VIEWER;
            player.changeToViewer();
        }
    }


    public void startNewGame(GamePanel gamePanel, SnakeProto.GameConfig.Builder settings, String name) {
        if (player != null) {
            player.stop();
            makeNewSocket();
        }
        player = new Master(this, gamePanel, settings, name, socket,
                new MessageResender(socket, settings.getPingDelayMs()));
        this.role = SnakeProto.NodeRole.MASTER;
        player.start();
    }

    public void displayErrorAndStopGame(Exception e) {
        JOptionPane.showMessageDialog(new JFrame(),
                "Something went wrong\n" + e.getMessage(),
                "App Error",
                JOptionPane.ERROR_MESSAGE);
        stop();
        app.closeApp();
    }

    public void stop() {
        if (player != null) {
            player.stop();
            try {
                app.stopMulticastListener();
            } catch (AppException appException) {
                JOptionPane.showMessageDialog(new JFrame(),
                        "Something went wrong\n" + appException.getMessage(),
                        "App Error",
                        JOptionPane.ERROR_MESSAGE);
            }
            socket.close();
        }

    }
}
