import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Coordinator {

    static final int BROADCAST_PORT = 4445;
    static final int COORD_PORT = 4447;
    static final int PORT = 4446;

    static boolean writing = false;

    int id;

    public Coordinator(int id) {
        this.id = id;
    }

    public void broadcast() throws IOException {
        System.out.println("> broadcast");
        InetAddress address = InetAddress.getByName("255.255.255.255");
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        try {
            String broadcastMessage = "coordinator " + this.id;
            byte[] buffer = broadcastMessage.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, BROADCAST_PORT);
            socket.send(packet);
            socket.close();

        } catch (Exception e) {
            System.out.println("Error on broadcast. " + e.getMessage());
            socket.close();
        }
    }

    public void receiveRequests() throws IOException {
        System.out.println("> receiveRequests");
        DatagramSocket socket = new DatagramSocket(COORD_PORT);

        try {
            byte[] receiveData = new byte[16];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                System.out.println("response: " + response);

                if (response.equals("write") || response.equals("read")) {
                    if (writing) {
                        SocketHelper.sendMessage(receivePacket.getAddress().getHostName(), "denied");
                    } else {
                        SocketHelper.sendMessage(receivePacket.getAddress().getHostName(), "granted");
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error on receiveRequests. " + e.getMessage());
            socket.close();
        }
    }

}