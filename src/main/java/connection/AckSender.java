package connection;

import proto.SnakeProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class AckSender {
    public static void sendAck(InetSocketAddress socketAddress, Long msgSeq,
                               DatagramSocket socket, int senderId,
                               int receiverId) throws IOException {
        SnakeProto.GameMessage.AckMsg ackMsg = SnakeProto.GameMessage.AckMsg.newBuilder().build();
        SnakeProto.GameMessage gameMessage = SnakeProto.GameMessage.newBuilder()
                .setAck(ackMsg)
                .setMsgSeq(msgSeq)
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .build();
        byte[] buf = gameMessage.toByteArray();
        DatagramPacket packet =
                new DatagramPacket(buf, buf.length, socketAddress.getAddress(), socketAddress.getPort());
        socket.send(packet);
    }

}
