package model;

import com.google.protobuf.InvalidProtocolBufferException;
import proto.SnakeProto;

import java.net.DatagramPacket;
import java.util.Arrays;

public class MessageParser {
    private DatagramPacket packet;
    private SnakeProto.GameMessage gameMsg;

    public void setPacket(DatagramPacket packet) {
        this.packet = packet;
        byte[] msg = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
        try {
            gameMsg = SnakeProto.GameMessage.parseFrom(msg);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    public String getHostName() {
        return gameMsg.getAnnouncement().getPlayers().getPlayersList().get(0).getName();
    }

    public String getSize() {
        return gameMsg.getAnnouncement().getConfig().getWidth() +
                " * " + gameMsg.getAnnouncement().getConfig().getHeight();
    }

    public String getFood() {
        return gameMsg.getAnnouncement().getConfig().getFoodStatic() +
                " + " + gameMsg.getAnnouncement().getConfig().getFoodPerPlayer() + "x";
    }
}
