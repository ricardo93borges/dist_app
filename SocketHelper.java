import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SocketHelper {

    static final int BROADCAST_PORT = 4445;
    static final int COORD_PORT = 4447;
    static final int PORT = 4446;

    public static void sendMessage(String host, String message) throws UnknownHostException, SocketException {
        System.out.println("> sendMessage " + message + " to " + host);
        InetAddress address = InetAddress.getByName(host);
        DatagramSocket socket = new DatagramSocket();

        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, PORT);
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            System.out.println("Error on sendMessage. " + e.getMessage());
            socket.close();
        }
    }

    public static String receiveMessage() throws IOException {
        System.out.println("> receiveMessage");
        DatagramSocket socket = new DatagramSocket(PORT);

        try {
            byte[] receiveData = new byte[16];

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            socket.close();

            return response;

        } catch (Exception e) {
            System.out.println("Error on receiveMessage. " + e.getMessage());
            socket.close();
            return null;
        }
    }
}