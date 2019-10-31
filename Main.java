
/**
 * Ricardo Borges
 */

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
        String[] coordinatorData = getCoordinator(lines);

        if (Integer.parseInt(coordinatorData[0]) == myId) {
            System.out.println("> is coordinator");

            Coordinator coordinator = new Coordinator(myId);

            // Tell the other I'm the coordnator
            coordinator.broadcast();

            // Wait for requests
            coordinator.receiveRequests();

        } else {
            String coordinatorHost = coordinatorData[1];
            System.out.println("> coordinatorHost: " + coordinatorHost);

            Node node = new Node(myId, coordinatorHost);

            // wait for broadcast message from coordinator
            String response = node.receiveBroadcast();
            int coordinatorId = Integer.parseInt(response.split(" ", 2)[1]);
            System.out.println("> coordinatorId: " + coordinatorId);

            // Send request to coordinator
            node.requestPermission("write");

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
    /*
     * public static void startElection(List<String> lines, int myId) {
     * System.out.println("> startElection"); try {
     * 
     * // Get hosts with IDs greater than mine
     * 
     * ArrayList<String> hosts = new ArrayList<>();
     * 
     * for (int i = 0; i < lines.size(); i++) { String[] data =
     * lines.get(i).split(" ", 3); int id = Integer.parseInt(data[0]); if (id >
     * myId) hosts.add(data[1]); }
     * 
     * // If there are no hosts with with greater than mine, I'm the coordinator if
     * (hosts.size() == 0) { // TODO }
     * 
     * for (int i = 0; i < lines.size(); i++) { // Send election message
     * sendMessage(lines.get(i), "election " + myId); }
     * 
     * } catch (Exception e) { System.out.println("Error on startElection. " +
     * e.getMessage()); } }
     */

}