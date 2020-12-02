package player;

import connection.MessageResender;
import model.Snake;

import java.net.DatagramSocket;

public class Normal implements Player{
    private Long msgSeq = 1L;
    private DatagramSocket socket;
    private int id;
    private final MessageResender messageResender;
    private Snake snake;


    public Normal(int id, MessageResender messageResender, DatagramSocket socket) {
        this.id = id;
        this.messageResender = messageResender;
        this.socket = socket;
    }



    private void sendSteerMessage() {

    }



    @Override
    public void start() {

    }

    @Override
    public void stop() {
        messageResender.interrupt();
    }
}
