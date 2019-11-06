import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Coordinator {

    boolean writing = false;

    int id;

    public Coordinator(int id) {
        this.id = id;
    }

    public boolean run() {

        try {
            // Tell the other I'm the coordnator
            this.broadcast();
        } catch (IOException e) {
            System.out.println("Error on Coordinator. " + e.getMessage());
        }

        while (true) {
            try {
                // Wait for requests
                this.receiveRequests();

            } catch (Exception e) {
                System.out.println("Error on Coordintor. " + e.getMessage());
                break;
            }
        }

        return false;
    }

    public void broadcast() throws IOException {
        System.out.println("> broadcast");
        InetAddress address = InetAddress.getByName(Constants.BROADCAST_HOST);
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        try {
            String broadcastMessage = "coordinator " + this.id;
            byte[] buffer = broadcastMessage.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Constants.BROADCAST_PORT);
            socket.send(packet);
            socket.close();

        } catch (Exception e) {
            System.out.println("Error on broadcast. " + e.getMessage());
            socket.close();
        }
    }

    public void receiveRequests() throws IOException {
        System.out.println("> receiveRequests");
        try {
            while (true) {
                Response response = SocketHelper.receiveMessage(Constants.COORD_PORT, 0);

                String r = "granted";
                if (response.message.equals("write") || response.message.equals("read")) {
                    if (this.writing) {
                        r = "denied";
                        SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "denied");
                    } else {
                        SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "granted");
                    }
                } else if (response.message.equals("release")) {
                    this.writing = false;
                }

                System.out
                        .println("> host " + response.hostname + " request " + response.message + ", response = " + r);
            }

        } catch (Exception e) {
            System.out.println("Error on receiveRequests. " + e.getMessage());
        }
    }

}