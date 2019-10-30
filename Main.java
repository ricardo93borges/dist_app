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
    static final int COORD_PORT = 4447;
    static final int PORT = 4446;

    static boolean writing = false;

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
        String[] coordinator = getCoordinator(lines);

        if (Integer.parseInt(coordinator[0]) == myId) {
            System.out.println("> is coordinator");

            // Tell the other I'm the coordnator
            broadcast(myId);
            // Wait for requests
            receiveRequests();

        } else {
            String coordinatorHost = coordinator[1];
            System.out.println("> coordinatorHost: " + coordinatorHost);

            // wait for broadcast message from coordinator
            String response = receiveBroadcast();
            int coordinatorId = Integer.parseInt(response.split(" ", 2)[1]);
            System.out.println("> coordinatorId: " + coordinatorId);

            // Send request to coordinator
            requestPermission(coordinatorHost, "write");

            // If request failed, start an election
            // startElection(lines, myId);

            // If I'm allowed, write in the file

        }

        TimeUnit.SECONDS.sleep(1);
    }

    public static String[] getCoordinator(List<String> lines) {
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

        return data;
    }

    public static void broadcast(int id) throws IOException {
        System.out.println("> broadcast");
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
            System.out.println("Error on broadcast. " + e.getMessage());
            socket.close();
        }
    }

    public static String receiveBroadcast() throws IOException {
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

    public static void receiveRequests() throws IOException {
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
                        sendMessage(receivePacket.getAddress().getHostName(), "denied");
                    } else {
                        sendMessage(receivePacket.getAddress().getHostName(), "granted");
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error on receiveRequests. " + e.getMessage());
            socket.close();
        }
    }

    /**
     * Request permission to coordinator
     * 
     * @type (write | read)
     */
    public static void requestPermission(String host, String type) throws IOException {
        System.out.println("> sendRequests");
        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName(host);
            System.out.println(address);

            byte[] buffer = type.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, COORD_PORT);
            socket.send(packet);
            socket.close();

            String response = receiveMessage();
            System.out.println("Response received: " + response);

            if (response.equals("granted")) {
                // TODO write
            }

        } catch (Exception e) {
            System.out.println("Error on requestPermission, " + e.getMessage());
            socket.close();
        }
    }

    public static void startElection(List<String> lines, int myId) {
        System.out.println("> startElection");
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
            System.out.println("Error on startElection. " + e.getMessage());
        }
    }

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