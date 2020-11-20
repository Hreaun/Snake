package player;

import proto.SnakeProto;

import java.net.DatagramSocket;
import java.net.SocketException;

public class Master implements Player {
    private DatagramSocket socket;
    private SnakeProto.GameConfig.Builder gameConfig;

    public Master(DatagramSocket socket, SnakeProto.GameConfig.Builder gameConfig) {
        this.socket = socket;
        this.gameConfig = gameConfig;
    }

    private void sendAnnounce() {
        // Узел с ролью MASTER рассылает сообщения
        // AnnouncementMsg с интервалом в 1 секунду на multicast-адрес 239.192.0.4, порт 9192.
    }

    @Override
    public void play() {
        try {
            socket.setSoTimeout(gameConfig.getStateDelayMs());
            //socket.receive();
        } catch (SocketException e) {
            e.printStackTrace();
        }


    }
}
