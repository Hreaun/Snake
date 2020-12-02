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

    private void setMessage(Long msgSeq, SnakeProto.GameMessage message) {
        synchronized (messages) {
            messages.put(msgSeq, message);
        }
    }

    public void setMessagesToResend(InetSocketAddress addr, Long msgSeq, SnakeProto.GameMessage message) {
        setMessage(msgSeq, message);
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
                System.out.println("remove " + msgSeq);
                messagesToResend.get(addr).remove(msgSeq);
                checkMessages();
            }
        }
    }

    void resendMessages() throws IOException {
        Map<Long, SnakeProto.GameMessage> messages = this.messages;
        synchronized (messagesToResend) {
            for (Map.Entry<InetSocketAddress, List<Long>> sentMsgEntry : messagesToResend.entrySet()) {
                synchronized (getMessageSeqs(sentMsgEntry.getKey())) {
                    for (Long msgSeq : sentMsgEntry.getValue()) {
                        SnakeProto.GameMessage msg = messages.get(msgSeq);
                        byte[] buf = msg.toByteArray();
                        DatagramPacket packet =
                                new DatagramPacket(buf, buf.length,
                                        sentMsgEntry.getKey().getAddress(),
                                        sentMsgEntry.getKey().getPort());
                        socket.send(packet);
                        System.out.println("resend " + msgSeq);
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                resendMessages();
                sleep(pingDelay);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                return;
            }
        }


    }
}
