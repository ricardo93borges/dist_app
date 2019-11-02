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
                    this.sendRelease();
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

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Constants.COORD_PORT);
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

    /**
     * Send release message to coordinator
     * 
     * @type (write | read)
     */
    public void sendRelease() throws IOException, SocketTimeoutException {
        System.out.println("> sendRelease");
        try {
            SocketHelper.sendMessage(this.coordinatorHost, Constants.BROADCAST_PORT, "release");
        } catch (Exception e) {
            System.out.println("Error on sendRelease, " + e.getMessage());
        }
    }

    public void listenElectionMessages() throws IOException {
        try {
            Response response = SocketHelper.receiveMessage(Constants.MESSAGE_ELECTION_PORT, 0);
            String id = response.message.split(" ", 2)[1];
            this.electionReceivedId = Integer.parseInt(id);
            this.electionStarted = true;

            SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "ack");

        } catch (Exception e) {
            System.out.println("Error on listenElectionMessages, " + e.getMessage());
        }
    }

    /**
     * 2 = this is the new coordinator, 1 = another node is the new coordinator
     * 
     * @param lines
     * @return int
     * 
     */
    public int startElection(List<String> lines) {
        System.out.println("> startElection");

        try {
            Boolean anyHostAnswered = false;
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
                this.electionReceivedId = 0;
                this.electionStarted = false;
                return 2;
            }

            for (int i = 0; i < lines.size(); i++) {
                // Send election message
                String message = "election " + this.id;
                SocketHelper.sendMessage(lines.get(i), Constants.MESSAGE_ELECTION_PORT, message);

                try {
                    SocketHelper.receiveMessage(Constants.MESSAGE_PORT, 1000 * 5);
                    anyHostAnswered = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("> Node " + lines.get(i) + " did not answered");
                    continue;
                }
            }

            if (anyHostAnswered) {
                return 1;
            }

            return 2;

        } catch (Exception e) {
            System.out.println("Error on startElection. " + e.getMessage());
            return 0;
        }
    }

}