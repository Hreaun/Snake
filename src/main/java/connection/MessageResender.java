package connection;

import proto.SnakeProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.*;

public class MessageResender extends Thread {
    private final DatagramSocket socket;
    private int pingDelay;
    private final Map<InetSocketAddress, List<Long>> messagesToResend = new HashMap<>();
    private final Map<Long, SnakeProto.GameMessage> messages = new HashMap<>();

    public MessageResender(DatagramSocket socket, int pingDelay) {
        this.socket = socket;
        this.pingDelay = pingDelay;
    }

    public void setMessagesToResend(InetSocketAddress addr, Long msgSeq) {
        synchronized (messagesToResend) {
            if (!messagesToResend.containsKey(addr)) {
                messagesToResend.put(addr, new ArrayList<>());
            }
            messagesToResend.get(addr).add(msgSeq);
        }
    }

    public List<Long> getMessageSeqs(InetSocketAddress addr) {
        List<Long> msgSeqs;
        synchronized (messagesToResend) {
            msgSeqs = messagesToResend.get(addr);
        }
        return msgSeqs;
    }

    private void checkMessages() {
        Iterator<Map.Entry<Long, SnakeProto.GameMessage>> messagesIter = messages.entrySet().iterator();
        while (messagesIter.hasNext()) {
            int counter = 0;
            Map.Entry<Long, SnakeProto.GameMessage> msgEntry = messagesIter.next();
            synchronized (messagesToResend) {
                for (Map.Entry<InetSocketAddress, List<Long>> messagesToResendEntry : messagesToResend.entrySet()) {
                    synchronized (this.getMessageSeqs(messagesToResendEntry.getKey())) {
                        if (messagesToResendEntry.getValue().contains(msgEntry.getKey())) {
                            counter++;
                        }
                    }
                }
                // удаление подтвержденного всеми соседями сообщения
                if (counter == 0) {
                    messagesIter.remove();
                }
            }
        }

    }

    public void removeMessage(InetSocketAddress addr, Long msgSeq) {
        synchronized (messagesToResend) {
            if (messagesToResend.containsKey(addr)) {
                messagesToResend.get(addr).remove(msgSeq);
                checkMessages();
            }
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            synchronized (messagesToResend) {
                messagesToResend.forEach((addr, msgSeqs) -> {
                    msgSeqs.forEach(msgSeq -> {
                        try {
                            byte[] buf = messages.get(msgSeq).toByteArray();
                            DatagramPacket packet =
                                    new DatagramPacket(buf, buf.length, addr.getAddress(), addr.getPort());
                            socket.send(packet);
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                            this.interrupt();
                        }
                    });
                });
                try {
                    sleep(pingDelay);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
