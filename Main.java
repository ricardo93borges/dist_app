import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    static final int BROADCAST_PORT = 4445;
    static final int PORT = 4446;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("Insufficient arguments");
            System.exit(1);
        }

        String filename = args[0];
        int line = Integer.parseInt(args[1]);

        List<String> lines = FileHelper.read(filename);

        // If I'm coordnator
        int myId = Integer.parseInt(lines.get(line).split(" ", 3)[0]);
        if (isCoordinator(lines, myId)) {
            // Tell the other I'm the coordnator
            broadcast(myId);
            // Wait for requests
            receveRequests();

        } else {
            String coordinatorHost = getCoordinatorHost(lines);
            // wait for broadcast message from coordinator
            String coordinatorId = receiveBroadcast();

            // Send request to coordinator
            sendRequests(coordinatorHost);

            // If request failed, start an election
            startElection(lines, myId);

            // If I'm allowed, write in the file

        }

        TimeUnit.SECONDS.sleep(1);
    }

    public static boolean isCoordinator(List<String> lines, int myId) {
        int greaterId = 0;

        for (int i = 0; i < lines.size(); i++) {
            String[] data = lines.get(i).split(" ", 3);
            int id = Integer.parseInt(data[0]);

            if (id > greaterId)
                greaterId = id;
        }

        if (greaterId == myId)
            return true;

        return false;
    }

    public static String getCoordinatorHost(List<String> lines) {
        int line = 0;
        int greaterId = 0;

        for (int i = 0; i < lines.size(); i++) {
            String[] data = lines.get(i).split(" ", 3);
            int id = Integer.parseInt(data[0]);

            if (id > greaterId) {
                greaterId = id;
                line = i;
                break;
            }
        }

        String[] data = lines.get(line).split(" ", 3);

        return data[1];
    }

    public static void broadcast(int id) throws IOException {
        InetAddress address = InetAddress.getByName("255.255.255.255");
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        try {
            String broadcastMessage = "coordinator " + id;
            byte[] buffer = broadcastMessage.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, BROADCAST_PORT);
            socket.send(packet);
            socket.close();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            socket.close();
        }
    }

    public static String receiveBroadcast() throws IOException {
        DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
        try {
            byte[] receiveData = new byte[8];

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            socket.close();

            return response;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            socket.close();
            return null;
        }
    }

    public static void receveRequests() throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);

        try {
            byte[] receiveData = new byte[8];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                socket.receive(receivePacket);
                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                System.out.println(response);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
            socket.close();
        }
    }

    public static void sendRequests(String host) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName(host);

            String message = "write string";
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, BROADCAST_PORT);
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            socket.close();
        }
    }

    public static void startElection(List<String> lines, int myId) {
        try {

            // Get hosts with IDs greater than mine

            ArrayList<String> hosts = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                String[] data = lines.get(i).split(" ", 3);
                int id = Integer.parseInt(data[0]);
                if (id > myId)
                    hosts.add(data[1]);
            }

            // If there are no hosts with with greater than mine, I'm the coordinator
            if (hosts.size() == 0) {
                // TODO
            }

            for (int i = 0; i < lines.size(); i++) {
                // Send election message
                sendMessage(lines.get(i), "election " + myId);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void sendMessage(String host, String message) throws UnknownHostException, SocketException {
        InetAddress address = InetAddress.getByName(host);
        DatagramSocket socket = new DatagramSocket();

        try {
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, BROADCAST_PORT);
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            socket.close();
        }
    }

}