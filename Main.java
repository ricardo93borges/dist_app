
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

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("Insufficient arguments");
            System.exit(1);
        }

        String filename = args[0];
        int line = Integer.parseInt(args[1]);

        List<String> lines = FileHelper.read(filename);

        int id = Integer.parseInt(lines.get(line).split(" ", 3)[0]);
        String[] coordinatorData = getCoordinator(lines);

        if (Integer.parseInt(coordinatorData[0]) == id) {
            System.out.println("> is coordinator");
            setupCoordinator(id);

        } else {
            String coordinatorHost = coordinatorData[1];
            setupNode(coordinatorHost, id);
        }
    }

    public static void setupCoordinator(int id) {
        Coordinator coordinator = new Coordinator(id);
        coordinator.run();
    }

    public static void setupNode(String coordinatorHost, int id) {
        Node node = new Node(id, coordinatorHost);
        node.run();

        /**
         * Node's run is a loop that only breaks if coordinator doesn't answer
         * (timeout), so if the program reach this line it has to start an election or
         * an election has started
         */

        if (node.electionStarted) {
            // TODO an election has started
            System.out.println("> Start election");
        } else {
            // TODO start election
            System.out.println("> Start election");
        }

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
}