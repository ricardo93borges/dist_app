import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SocketHelper {

    /**
     * Send message via socket
     * 
     * @param String host
     * @param int    port
     * @param String message
     * @throws UnknownHostException
     * @throws SocketException
     */
    public static void sendMessage(String host, int port, String message) throws UnknownHostException, SocketException {
        InetAddress address = InetAddress.getByName(host);
        DatagramSocket socket = new DatagramSocket();

        System.out.println("[] Send to " + host + ":" + port);

        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);

            socket.send(packet);
            socket.close();
        } catch (IOException e) {
            System.out.println("[SocketHelper] Error on sendMessage. " + e.getMessage());
            socket.close();
        } finally {
            socket.close();
        }
    }

    /**
     * Receive message via socket
     * 
     * @param port
     * @param timeout
     * @return Response
     * @throws IOException
     */
    public static Response receiveMessage(int port, int timeout) throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        socket.setSoTimeout(timeout);

        try {
            byte[] receiveData = new byte[16];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            socket.receive(receivePacket);
            String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
            String hostname = receivePacket.getAddress().getHostName();
            int messagePort = receivePacket.getPort();
            socket.close();

            return new Response(message, hostname, messagePort);

        } catch (IOException e) {
            System.out.println("[SocketHelper] Error on receiveMessage. " + e.getMessage());
            socket.close();
            return null;
        } finally {
            socket.close();
        }
    }
}