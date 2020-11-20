package connection;

import model.App;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MulticastListener extends Thread {
    private MulticastSocket multicastSocket = null;
    private App app;
    private final int PORT = 9192;
    private final String IP_ADDR = "239.192.0.4";

    public MulticastListener(App app) throws IOException {
        this.app = app;
        try {
            multicastSocket = new MulticastSocket(PORT);
            multicastSocket.joinGroup(InetAddress.getByName(IP_ADDR));
        } catch (IOException e) {
            System.out.println(e.getMessage());
            if (multicastSocket != null) {
                multicastSocket.close();
            }
            throw e;
        }
    }


    @Override
    public void run() {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (true) {
            try {
                multicastSocket.receive(packet);
                app.addGame(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
