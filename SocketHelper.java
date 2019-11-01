import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SocketHelper {

    public static void sendMessage(String host, int port, String message) throws UnknownHostException, SocketException {
        System.out.println("> sendMessage " + message + " to " + host);
        InetAddress address = InetAddress.getByName(host);
        DatagramSocket socket = new DatagramSocket();

        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            System.out.println("Error on sendMessage. " + e.getMessage());
            socket.close();
        }
    }

    public static Response receiveMessage(int port, int timeout) throws IOException {
        System.out.println("> receiveMessage");
        DatagramSocket socket = new DatagramSocket(port);
        socket.setSoTimeout(timeout);

        try {
            byte[] receiveData = new byte[16];

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            socket.receive(receivePacket);
            String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
            String hostname = receivePacket.getAddress().getHostName();
            socket.close();

            return new Response(message, hostname);

        } catch (Exception e) {
            System.out.println("Error on receiveMessage. " + e.getMessage());
            socket.close();
            return null;
        }
    }
}