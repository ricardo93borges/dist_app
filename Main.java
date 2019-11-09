
/**
 * Ricardo Borges
 */

import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("Insufficient arguments");
            System.exit(1);
        }

        String filename = args[0];
        int line = Integer.parseInt(args[1]);

        List<String> lines = FileHelper.read(filename);

        String[] data = lines.get(line).split(" ", 3);

        int id = Integer.parseInt(data[0]);
        String host = data[1];
        String port = data[2];

        String[] coordinatorData = getCoordinator(lines);

        if (Integer.parseInt(coordinatorData[0]) == id) {
            System.out.println("> is coordinator");
            setupCoordinator(id);

        } else {
            String coordinatorHost = coordinatorData[1];
            int coordinatorPort = Integer.parseInt(coordinatorData[2]);
            System.out.println("coordinatorHost: " + coordinatorHost);
            setupNode(coordinatorHost, coordinatorPort, id, host, Integer.parseInt(port), lines);
        }
    }

    public static void setupCoordinator(int id) {
        Coordinator coordinator = new Coordinator(id);
        coordinator.run();
    }

    public static void setupNode(String coordinatorHost, int coordinatorPort, int id, String host, int port,
            List<String> lines) {
        Node node = new Node(id, host, port, coordinatorHost, coordinatorPort, lines);
        node.run();

        /**
         * Node's run is a loop that only breaks if coordinator doesn't answer
         * (timeout), so if the program reach this line it has to start an election or
         * an election has started
         */

        int response = node.startElection(lines);
        node.electionListener.interrupt();
        node = null;
        if (response == 1) {
            setupNode(null, 0, id, host, port, lines);
        } else {
            setupCoordinator(id);
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
            }
        }

        String[] data = lines.get(line).split(" ", 3);
        return data;
    }
}