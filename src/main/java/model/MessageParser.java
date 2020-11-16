package model;

import com.google.protobuf.InvalidProtocolBufferException;
import proto.SnakeProto;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<String> name = new AtomicReference<>("");
        gameMsg.getAnnouncement().getPlayers().getPlayersList().forEach(player -> {
            if (player.getIpAddress().equals(packet.getAddress().toString())
                    && player.getPort() == packet.getPort()) {
                name.set(player.getName());
            }
        });
        return name.get();
    }

    public String getSize() {
        return gameMsg.getAnnouncement().getConfig().getWidth() +
                " * " + gameMsg.getAnnouncement().getConfig().getHeight();
    }

    public String getFood(){
        return gameMsg.getAnnouncement().getConfig().getFoodStatic() +
                " + " + gameMsg.getAnnouncement().getConfig().getFoodPerPlayer() + "x";
    }
}
