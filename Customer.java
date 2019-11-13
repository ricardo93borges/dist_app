import java.nio.channels.SocketChannel;
import java.util.List;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class Customer {
    private static final String ELECTION = "election";

    int id;
    String host;
    int port;
    SocketChannel sc;

    String coordinatorHost;
    int coordinatorPort;
    boolean electionStarted;
    int electionReceivedId;
    int electionPort;
    Thread electionListener;
    List<String> lines;
    boolean waiting = false;
    boolean requestSent = false;

    public Customer(int id, String host, int port, SocketChannel sc, String coordinatorHost, int coordinatorPort,
            List<String> lines) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.sc = sc;
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
        this.electionStarted = false;
        this.electionReceivedId = 0;
        this.electionListener = new Thread();
        this.lines = lines;
    }

    public void setElectionStarted(boolean electionStarted) {
        this.electionStarted = electionStarted;
    }

    public void setElectionReceivedId(int electionReceivedId) {
        this.electionReceivedId = electionReceivedId;
    }

    public void setElectionPort(int port) {
        this.electionPort = port;
    }

    public int getId() {
        return this.id;
    }

    public boolean isThreadRunning(String name) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (t.getName().equals(name))
                return true;
        }
        return false;
    }

    public int run() {
        System.out.println("ID: " + this.id);
        System.out.println("Host: " + this.host + ":" + this.port);

        if (this.coordinatorHost == null) {
            // wait for broadcast message from coordinator
            try {
                String response = this.receiveBroadcast();
                String id = response.split(" ", 2)[1];
                Coordinator coordinator = this.getCoordinatorById(Integer.parseInt(id));
                this.coordinatorHost = coordinator.getHost();
                this.coordinatorPort = coordinator.getPort();
            } catch (IOException e) {
                System.out.println("[Customer] Error on Node. " + e.getMessage());
            }
        }

        System.out.println("[Customer] Coordinator host: " + this.coordinatorHost + ":" + this.coordinatorPort);

        try {
            this.listenElectionMessages();
        } catch (IOException e) {
            System.out.println("[Customer] Error on listen election messages. " + e.getMessage());
        }

        InetSocketAddress addr = new InetSocketAddress(this.coordinatorHost, this.coordinatorPort);
        Selector selector = null;
        SocketChannel sc = null;

        try {
            selector = Selector.open();
            sc = SocketChannel.open();
            sc.configureBlocking(false);
            sc.connect(addr);
            sc.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        } catch (IOException e) {
            System.out.println("[Customer] Error on connect: " + e.getMessage());
        }

        Response res = null;
        while (true) {
            try {
                if (this.electionStarted) {
                    System.out.println("[Customer] Election started ");
                    break;
                }

                if (selector.select() > 0) {
                    this.process(selector.selectedKeys());
                }

                if (this.requestSent) {
                    res = SocketHelper.receiveMessage(this.port, Constants.TIMOUT);
                } else {
                    System.out.println("[Customer] send request ");
                    SocketHelper.sendMessage(this.coordinatorHost, this.coordinatorPort, "request " + this.id);
                    this.requestSent = true;
                    res = SocketHelper.receiveMessage(this.port, Constants.TIMOUT);
                }

                System.out.println("[Customer] received " + res.message);
                this.requestSent = false;

                if (res.message.equals("granted")) {
                    SocketHelper.sendMessage(this.coordinatorHost, Constants.BARBER_PORT,
                            "acquier " + Integer.toString(this.id));
                }
                TimeUnit.SECONDS.sleep(2);

            } catch (Exception e) {
                // System.out.println("[Customer] Error, continue " + e.getMessage());
                continue;
            }
        }

        try {
            sc.close();
        } catch (IOException e) {
            System.out.println("[Customer] Error on close connection: " + e.getMessage());
        }

        return this.startElection(lines);
    }

    public Boolean process(Set readySet) throws Exception {
        SelectionKey key = null;
        Iterator iterator = null;
        iterator = readySet.iterator();

        while (iterator.hasNext()) {
            key = (SelectionKey) iterator.next();
            iterator.remove();
        }

        if (key.isConnectable()) {
            Boolean connected = this.handleConnect(key);
            if (!connected) {
                return true;
            }
        }

        if (key.isReadable()) {
            SocketChannel sc = (SocketChannel) key.channel();
            ByteBuffer bb = ByteBuffer.allocate(1024);
            sc.read(bb);

            String message = new String(bb.array()).trim();
            System.out.println("[Customer] Received: " + message);

            if (message.length() <= 0) {
                sc.close();
                System.out.println("Connection closed...");
                setElectionStarted(true);
            }
        }

        return false;
    }

    public Boolean handleConnect(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            while (sc.isConnectionPending()) {
                sc.finishConnect();
            }
        } catch (IOException e) {
            key.cancel();
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Receive broadcast message from coordinator
     * 
     * @return String message
     * @throws IOException
     */
    public String receiveBroadcast() throws IOException {
        System.out.println("[Customer] receiveBroadcast ");
        try {
            Response response = SocketHelper.receiveMessage(this.port, 0);
            System.out.println("[Customer] Broadcast message received: " + response.message);
            return response.message;
        } catch (Exception e) {
            System.out.println("[Customer] Error on receiveBroadcast. " + e.getMessage());
            return null;
        }
    }

    /**
     * Listen to election messages
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
                        DatagramSocket socket = null;

                        for (int port : Constants.MESSAGE_ELECTION_PORTS) {
                            try {
                                socket = new DatagramSocket(port);
                                socket.setSoTimeout(0);
                                setElectionPort(port);
                                break;
                            } catch (IOException ex) {
                                continue; // try next port
                            }
                        }

                        Response response = SocketHelper.receiveMessage(socket);
                        String id = response.message.split(" ", 2)[1];

                        setElectionStarted(true);
                        setElectionReceivedId(Integer.parseInt(id));

                        System.out.println("[Customer] Election message received " + response.message);
                        int port = getPortById(id);

                        System.out.println("[Customer] Sending ack to " + response.hostname + ":" + port);

                        if (getId() > Integer.parseInt(id))
                            SocketHelper.sendMessage(response.hostname, port, "ack");

                    } catch (IOException e) {
                        System.out.println("[Customer] Error on listenElectionMessages thread, " + e.getMessage());
                    }
                }
            });

            this.electionListener.setName("ElectionListener");
            this.electionListener.start();

        } catch (Exception e) {
            System.out.println("[Customer] Error on listenElectionMessages, " + e.getMessage());
        }
    }

    /**
     * Start election
     * 
     * @param List<String> lines
     * @return int (2 = this is the new coordinator, 1 = another node is)
     */
    public int startElection(List<String> lines) {
        System.out.println("[Customer] Start election");
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
                String hostToSend = hosts.get(i);

                for (int port : Constants.MESSAGE_ELECTION_PORTS) {
                    if (hostToSend.equals(this.host) && port == this.electionPort)
                        continue;

                    SocketHelper.sendMessage(hostToSend, port, message);
                }

                try {
                    Response response = SocketHelper.receiveMessage(this.port, Constants.TIMOUT);
                    System.out.println("[Customer] election response received: " + response.message);

                    if (response.message != null) {
                        anyHostAnswered = true;
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("[Customer] Node " + lines.get(i) + " did not answered");
                    continue;
                }
            }

            if (anyHostAnswered)
                return 1;
            return 2;

        } catch (Exception e) {
            System.out.println("[Customer] error on election. " + e.getMessage());
            if (anyHostAnswered)
                return 1;
            return 2;
        }
    }

    public Coordinator getCoordinatorById(int id) {
        for (String line : this.lines) {
            String[] data = line.split(" ", 3);
            if (Integer.parseInt(data[0]) == id)
                return new Coordinator(id, data[1], Integer.parseInt(data[2]), null);
        }
        return null;
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