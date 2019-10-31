import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Node {

    static final int BROADCAST_PORT = 4445;
    static final int COORD_PORT = 4447;
    static final int PORT = 4446;

    int id;
    String coordinatorHost;

    public Node(int id, String coordinatorHost) {
        this.id = id;
        this.coordinatorHost = coordinatorHost;
    }

    public String receiveBroadcast() throws IOException {
        System.out.println("> receiveBroadcast");
        DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
        try {
            byte[] receiveData = new byte[16];

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            socket.close();

            return response;
        } catch (Exception e) {
            System.out.println("Error on receiveBroadcast. " + e.getMessage());
            socket.close();
            return null;
        }
    }

    /**
     * Request permission to coordinator
     * 
     * @type (write | read)
     */
    public void requestPermission(String type) throws IOException {
        System.out.println("> sendRequests");
        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName(this.coordinatorHost);
            System.out.println(address);

            byte[] buffer = type.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, COORD_PORT);
            socket.send(packet);
            socket.close();

            String response = SocketHelper.receiveMessage();
            System.out.println("Response received: " + response);

            if (response.equals("granted")) {
                // TODO write
            }

        } catch (Exception e) {
            System.out.println("Error on requestPermission, " + e.getMessage());
            socket.close();
        }
    }

}