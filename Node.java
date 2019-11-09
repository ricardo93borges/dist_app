import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Node {

    private static final String WRITE = "write";
    private static final String READ = "read";
    private static final String ELECTION = "election";

    int id;
    String host;
    int port;
    String coordinatorHost;
    int coordinatorPort;
    boolean electionStarted;
    int electionReceivedId;
    Thread electionListener;
    List<String> lines;
    int action;

    public Node(int id, String host, int port, String coordinatorHost, int coordinatorPort, List<String> lines) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
        this.electionStarted = false;
        this.electionReceivedId = 0;
        this.electionListener = new Thread();
        this.lines = lines;
        this.action = 1;
    }

    public void setElectionStarted(boolean electionStarted) {
        this.electionStarted = electionStarted;
    }

    public void setElectionReceivedId(int electionReceivedId) {
        this.electionReceivedId = electionReceivedId;
    }

    public boolean isThreadRunning(String name) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (t.getName().equals(name))
                return true;
        }
        return false;
    }

    public boolean run() {
        if (this.coordinatorHost == null) {
            // wait for broadcast message from coordinator
            try {
                String response = this.receiveBroadcast();
                String id = response.split(" ", 2)[1];
                this.coordinatorHost = this.getHostById(Integer.parseInt(id));
            } catch (IOException e) {
                System.out.println("[Node] Error on Node. " + e.getMessage());
            }
        }

        try {
            this.listenElectionMessages();
        } catch (IOException e) {
            System.out.println("[Node] Error on listen election messages. " + e.getMessage());
        }

        while (true) {
            try {
                if (this.electionStarted) {
                    System.out.println("> Election started ");
                    break;
                }

                String permission = this.requestPermission(WRITE);

                /*
                 * if (permission.equals("denied")) {
                 * System.out.println("[Node] Wait room full"); } else if
                 * (permission.equals("done")) { System.out.println("[Node] got haircut"); }
                 */

                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                System.out.println("[Node] " + e.getMessage());
                break;
            }
        }

        return false;
    }

    /**
     * Get host from config file by id
     * 
     * @param id
     * @return String host
     */
    public String getHostById(int id) {
        for (String line : this.lines) {
            String[] data = line.split(" ", 3);
            if (Integer.parseInt(data[0]) == id)
                return data[1];
        }
        return null;
    }

    /**
     * Receive broadcast message from coordinator
     * 
     * @return String message
     * @throws IOException
     */
    public String receiveBroadcast() throws IOException {
        try {
            Response response = SocketHelper.receiveMessage(Constants.BROADCAST_PORT, 0);
            System.out.println("[Node] Broadcast message received: " + response.message);
            return response.message;
        } catch (Exception e) {
            System.out.println("[Node] Error on receiveBroadcast. " + e.getMessage());
            return null;
        }
    }

    /**
     * Request permission to coordinator
     * 
     * @param type (write or read)
     * @return String message (granted or denied)
     * @throws IOException
     * @throws SocketTimeoutException
     */
    public String requestPermission(String type) throws IOException, SocketTimeoutException {
        System.out.println("[Node] Request permission for " + type + " to " + this.coordinatorHost);
        try {
            SocketHelper.sendMessage(this.coordinatorHost, Constants.COORD_PORT, Integer.toString(this.id));

            Response response = SocketHelper.receiveMessage(this.port, Constants.TIMOUT);
            System.out.println("[Node] Permission: " + response.message);

            return response.message;

        } catch (Exception e) {
            System.out.println("[Node] Error on requestPermission, " + e.getMessage());
            return null;
        }
    }

    /**
     * Send release message to coordinator
     * 
     * @throws IOException
     * @throws SocketTimeoutException
     */
    public void sendRelease() throws IOException, SocketTimeoutException {
        System.out.println("[Node] Send release");
        try {
            SocketHelper.sendMessage(this.coordinatorHost, Constants.COORD_PORT, "release");
        } catch (Exception e) {
            System.out.println("[Node] Error on sendRelease, " + e.getMessage());
        }
    }

    /**
     * Send write message to Archive
     * 
     * @throws IOException
     * @throws SocketTimeoutException
     */
    public void sendWrite() throws IOException, SocketTimeoutException {
        System.out.println("[Node] Send " + WRITE);
        try {
            String message = this.id + " " + WRITE + " " + StringGenerator.generate();
            SocketHelper.sendMessage(Constants.ARCHIVE_HOST, Constants.ARCHIVE_PORT, message);
            SocketHelper.receiveMessage(Constants.MESSAGE_PORT, 0);

        } catch (Exception e) {
            System.out.println("[Node] Error on sendWrite, " + e.getMessage());
        }
    }

    /**
     * Send read message to Archive
     * 
     * @throws IOException
     * @throws SocketTimeoutException
     */
    public void sendRead() throws IOException, SocketTimeoutException {
        System.out.println("[Node] Send " + READ);
        try {
            SocketHelper.sendMessage(Constants.ARCHIVE_HOST, Constants.ARCHIVE_PORT, this.id + " " + READ);
            Response response = SocketHelper.receiveMessage(Constants.MESSAGE_PORT, 0);
            System.out.println("[Node] line received: " + response.message);
        } catch (Exception e) {
            System.out.println("[Node] Error on sendRead, " + e.getMessage());
        }
    }

    /**
     * Listend to election messages
     * 
     * @throws IOException
     */
    public void listenElectionMessages() throws IOException {
        if (isThreadRunning("ElectionListener")) {
            return;
        }

        try {
            this.electionListener = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Response response = SocketHelper.receiveMessage(Constants.MESSAGE_ELECTION_PORT, 0);
                        String id = response.message.split(" ", 2)[1];
                        setElectionStarted(true);
                        setElectionReceivedId(Integer.parseInt(id));
                        SocketHelper.sendMessage(response.hostname, getPortById(id), "ack");
                    } catch (Exception e) {
                        System.out.println("[Node] Error on listenElectionMessages thread, " + e.getMessage());
                    }
                }
            });

            this.electionListener.setName("ElectionListener");
            this.electionListener.start();

        } catch (Exception e) {
            System.out.println("[Node] Error on listenElectionMessages, " + e.getMessage());
        }
    }

    /**
     * Start election
     * 
     * @param List<String> lines
     * @return int (2 = this is the new coordinator, 1 = another node is)
     */
    public int startElection(List<String> lines) {
        System.out.println("[Node] Start election");
        Boolean anyHostAnswered = false;
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
                this.electionReceivedId = 0;
                this.electionStarted = false;
                return 2;
            }

            for (int i = 0; i < hosts.size(); i++) {
                // Send election message
                String message = ELECTION + " " + this.id;
                String host = hosts.get(i);
                SocketHelper.sendMessage(host, Constants.MESSAGE_ELECTION_PORT, message);
                try {
                    Response response = SocketHelper.receiveMessage(Constants.MESSAGE_PORT, Constants.TIMOUT);

                    if (response.message != null) {
                        anyHostAnswered = true;
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("[Node] Node " + lines.get(i) + " did not answered");
                    continue;
                }
            }

            if (anyHostAnswered)
                return 1;
            return 2;

        } catch (Exception e) {
            if (anyHostAnswered)
                return 1;
            return 2;
        }
    }

    public int getPortById(String id) {
        for (String line : this.lines) {
            String[] data = line.split(" ", 3);
            if (data[0].equals(id))
                return Integer.parseInt(data[2]);
        }
        return 0;
    }
}
