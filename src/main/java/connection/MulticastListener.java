package connection;

import model.App;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MulticastListener extends Thread {
    private MulticastSocket multicastSocket = null;
    private App app;
    private final int PORT = 9192;
    private final String IP_ADDR = "239.192.0.4";
    private final Map<InetSocketAddress, Long> lastMessageTime = new HashMap<>();

    public MulticastListener(App app) {
        this.app = app;
        try {
            multicastSocket = new MulticastSocket(PORT);
            multicastSocket.joinGroup(InetAddress.getByName(IP_ADDR));
        } catch (IOException e) {
            System.out.println(e.getMessage());
            if (multicastSocket != null) {
                multicastSocket.close();
            }
        }
    }

    private void updateLastMessageTime(InetSocketAddress socketAddress) {
        lastMessageTime.put(socketAddress, Instant.now().toEpochMilli());
    }

    private void checkLastMessageTime() {
        Iterator<Map.Entry<InetSocketAddress, Long>> it = lastMessageTime.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<InetSocketAddress, Long> lastTime = it.next();
            if (Instant.now().toEpochMilli() - lastTime.getValue() > 1_200) {
                app.deleteGame(lastTime.getKey());
                it.remove();
            }
        }
    }

    public void closeSocket() {
        multicastSocket.close();
    }


    @Override
    public void run() {
        byte[] buf = new byte[128];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (!isInterrupted()) {
            try {
                multicastSocket.setSoTimeout(300);
                multicastSocket.receive(packet);
                updateLastMessageTime((InetSocketAddress) packet.getSocketAddress());
                app.addGame(packet);
            } catch (SocketTimeoutException e) {
                checkLastMessageTime();
            } catch (IOException e) {
                return;
            }
        }
    }

}
