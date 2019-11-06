import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Node {

    int id;
    String host;
    String port;
    String coordinatorHost;
    boolean electionStarted;
    int electionReceivedId;
    Thread electionListener;
    List<String> lines;
    int action;

    public Node(int id, String host, String port, String coordinatorHost, List<String> lines) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.coordinatorHost = coordinatorHost;
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
        // wait for broadcast message from coordinator
        if (this.coordinatorHost == null) {
            try {
                String response = this.receiveBroadcast();
                System.out.println("[Node] Res. " + response);
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

                if (this.action == 1) {
                    String permission = this.requestPermission("write");

                    if (permission == null)
                        break;

                    this.sendWrite();
                    this.action = 0;

                } else {
                    String permission = this.requestPermission("read");

                    if (permission == null)
                        break;

                    this.sendRead();
                    this.action = 1;
                }

                this.sendRelease();

                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                System.out.println("[Node] " + e.getMessage());
                break;
            }
        }

        return false;
    }

    public String getHostById(int id) {
        for (String line : this.lines) {
            String[] data = line.split(" ", 3);
            if (Integer.parseInt(data[0]) == id)
                return data[1];
        }
        return null;
    }

    public String receiveBroadcast() throws IOException {
        System.out.println("> receiveBroadcast");
        try {
            Response response = SocketHelper.receiveMessage(Constants.BROADCAST_PORT, 0);
            return response.message;
        } catch (Exception e) {
            System.out.println("[Node] Error on receiveBroadcast. " + e.getMessage());
            return null;
        }
    }

    /**
     * Request permission to coordinator
     * 
     * @type (write | read)
     */
    public String requestPermission(String type) throws IOException, SocketTimeoutException {
        System.out.println("> requestPermission to " + this.coordinatorHost);
        try {
            SocketHelper.sendMessage(this.coordinatorHost, Constants.COORD_PORT, type);

            Response response = SocketHelper.receiveMessage(Constants.MESSAGE_PORT, Constants.TIMOUT);
            System.out.println("Response received: " + response.message);

            return response.message;

        } catch (Exception e) {
            System.out.println("[Node] Error on requestPermission, " + e.getMessage());
            return null;
        }
    }

    public void sendRelease() throws IOException, SocketTimeoutException {
        System.out.println("> sendRelease");
        try {
            SocketHelper.sendMessage(this.coordinatorHost, Constants.COORD_PORT, "release");
        } catch (Exception e) {
            System.out.println("[Node] Error on sendRelease, " + e.getMessage());
        }
    }

    public void sendWrite() throws IOException, SocketTimeoutException {
        System.out.println("> sendWrite");
        try {
            String message = this.id + " write " + StringGenerator.generate();
            SocketHelper.sendMessage(Constants.ARCHIVE_HOST, Constants.ARCHIVE_PORT, message);

        } catch (Exception e) {
            System.out.println("[Node] Error on sendWrite, " + e.getMessage());
        }
    }

    public void sendRead() throws IOException, SocketTimeoutException {
        System.out.println("> sendRead");
        try {
            SocketHelper.sendMessage(Constants.ARCHIVE_HOST, Constants.ARCHIVE_PORT, this.id + " read");
            Response response = SocketHelper.receiveMessage(Constants.MESSAGE_PORT, 0);
            System.out.println("[Node] read line: " + response.message);
        } catch (Exception e) {
            System.out.println("[Node] Error on sendRead, " + e.getMessage());
        }
    }

    public void listenElectionMessages() throws IOException {
        System.out.println("> listenElectionMessages");

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
                        SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "ack");
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
     * 2 = this is the new coordinator, 1 = another node is the new coordinator
     * 
     * @param lines
     * @return int
     * 
     */
    public int startElection(List<String> lines) {
        System.out.println("> startElection");
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
                String message = "election " + this.id;
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
}
