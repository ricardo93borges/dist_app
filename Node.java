import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Node {

    int id;
    String coordinatorHost;
    boolean electionStarted;
    int electionReceivedId;

    public Node(int id, String coordinatorHost) {
        this.id = id;
        this.coordinatorHost = coordinatorHost;
        this.electionStarted = false;
        this.electionReceivedId = 0;
    }

    public boolean run() {
        /*
         * try { // wait for broadcast message from coordinator this.receiveBroadcast();
         * } catch (IOException e) { System.out.println("Error on Node. " +
         * e.getMessage()); }
         */

        while (true) {
            try {

                if (this.electionStarted) {
                    break;
                }

                // Request permission to coordinator
                String permission = this.requestPermission("write");

                if (permission == null)
                    break;

                if (permission.equals("granted")) {
                    // TODO write
                }

                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                System.out.println("Error on Node. " + e.getMessage());
                break;
            }
        }

        return false;
    }

    public String receiveBroadcast() throws IOException {
        System.out.println("> receiveBroadcast");
        DatagramSocket socket = new DatagramSocket(Constants.BROADCAST_PORT);
        try {
            byte[] receiveData = new byte[16];

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            socket.close();

            return response;
        } catch (Exception e) {
            System.out.println("Error on receiveBroadcast. " + e.getMessage());
            return null;
        } finally {
            socket.close();
        }
    }

    /**
     * Request permission to coordinator
     * 
     * @type (write | read)
     */
    public String requestPermission(String type) throws IOException, SocketTimeoutException {
        DatagramSocket socket = new DatagramSocket();
        try {
            InetAddress address = InetAddress.getByName(this.coordinatorHost);
            System.out.println(address);

            byte[] buffer = type.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Constants.BROADCAST_PORT);
            socket.send(packet);
            socket.close();

            int port = Constants.MESSAGE_PORT;
            int timeout = 1000 * 10;
            Response response = SocketHelper.receiveMessage(port, timeout);
            System.out.println("Response received: " + response.message);

            return response.message;

        } catch (Exception e) {
            System.out.println("Error on requestPermission, " + e.getMessage());
            socket.close();
            return "";
        }
    }

    public void listenElectionMessages() throws IOException {
        try {
            Response response = SocketHelper.receiveMessage(Constants.MESSAGE_ELECTION_PORT, 0);
            String id = response.message.split(" ", 2)[1];
            this.electionReceivedId = Integer.parseInt(id);
        } catch (Exception e) {
            System.out.println("Error on listenElectionMessages, " + e.getMessage());
        }
    }

    public void startElection(List<String> lines) {
        System.out.println("> startElection");

        try {
            // Get hosts with IDs greater than mine
            ArrayList<String> hosts = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                String[] data = lines.get(i).split(" ", 3);
                int id = Integer.parseInt(data[0]);
                if (id > this.id)
                    hosts.add(data[1]);
            }

            // If there are no hosts with id greater than mine, I'm the coordinator
            if (hosts.size() == 0) {
                // TODO
            }

            for (int i = 0; i < lines.size(); i++) {
                // Send election message
                String message = "election " + this.id;
                SocketHelper.sendMessage(lines.get(i), Constants.MESSAGE_ELECTION_PORT, message);
            }

        } catch (Exception e) {
            System.out.println("Error on startElection. " + e.getMessage());
        }
    }

}