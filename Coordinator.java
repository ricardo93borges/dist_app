import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.TimeUnit;

public class Coordinator {

    // number of chairs in the waiting room
    public static final int MAX_CHAIRS = 2;

    // socket
    ServerSocketChannel serverSocketChannel;

    // number of customers waiting
    int customers = 0;

    // seats available for hair cutting
    int seats = 1;

    // barber is idle or working
    int barber = 0;

    // Customers queue
    ArrayList<Customer> list = new ArrayList<Customer>();

    // Customers to notify
    ArrayBlockingQueue<Customer> notifyQueue = new ArrayBlockingQueue<Customer>(1000);

    int id;
    int port;
    String host;
    List<String> lines;

    public Coordinator(int id, String host, int port, List<String> lines) {
        this.id = id;
        this.lines = lines;
        this.port = port;
        this.host = host;
    }

    public void upCustomer() {
        this.customers++;
    }

    public void downCustomer() {
        this.customers--;
    }

    public void upBarber() {
        this.barber++;
    }

    public void downBarber() {
        this.barber--;
    }

    public void upSeats() {
        this.seats++;
    }

    public void downSeats() {
        this.seats--;
    }

    public boolean addCustomerToQueue(Customer customer) {
        this.list.add(customer);
        return true;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public Boolean run() {
        this.port = this.getPortById(this.id);
        this.host = this.getHostById(this.id);

        System.out.println("id: " + id);
        System.out.println("host: " + this.host + ":" + this.port);

        // Broadcast thread
        Thread broadcastThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try { // tell the other I'm the coordinator
                    broadcast();
                } catch (IOException e) {
                    System.out.println("[Coordinator] error on try broadcast " + e.getMessage());
                }
            }
        });
        broadcastThread.setName("broadcastThread");
        broadcastThread.start();

        // Connectionts handler thread
        Thread connectionsHandler = new Thread(new Runnable() {

            @Override
            public void run() {
                handleConnections();
            }
        });
        connectionsHandler.setName("connectionsHandler");
        connectionsHandler.start();

        // Barber listener thread
        Thread barberListener = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    listenBarber();
                } catch (IOException e) {
                    System.out.println("[Coordinator] error on barber listener thread " + e.getMessage());
                }
            }
        });
        barberListener.setName("barberListener");
        barberListener.start();

        // list proccess thread
        Thread listProcessor = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    processList();
                } catch (IOException e) {
                    System.out.println("[Coordinator] error on list process thread " + e.getMessage());
                }
            }
        });
        listProcessor.setName("listProcessor");
        listProcessor.start();

        this.process();

        return false;
    }

    public void process() {
        while (true) {
            try {
                Response res = SocketHelper.receiveMessage(this.port, 0);
                // System.out.println("[Coordinator] recv: " + res.message);

                String[] split = res.message.split(" ");

                if (!split[0].equals("request"))
                    continue;

                String id = split[1];

                Customer c = this.getCustomerById(id, null);

                // System.out.println("[Coordinator] list: " + list.size() + ", customers: " +
                // this.customers);

                if (this.customers < MAX_CHAIRS) {
                    upCustomer();
                    list.add(c);
                } else {
                    SocketHelper.sendMessage(c.host, c.port, "denied");
                }

            } catch (Exception e) {
                System.out.println("[Coordinator] error on process. " + e.getMessage());
                continue;
            }
        }
    }

    public void handleConnections() {
        System.out.println("[Coordinator] handleConnections");
        try {
            Selector selector = Selector.open();

            this.serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(this.host, this.port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, SelectionKey.OP_WRITE);

            SelectionKey key = null;
            while (true) {
                if (selector.select() <= 0)
                    continue;

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    key = (SelectionKey) iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()) {
                        SocketChannel sc = serverSocketChannel.accept();
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ);
                        System.out.println("[Coordinator] Connected: " + sc.getLocalAddress());
                    }

                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer bb = ByteBuffer.allocate(1024);
                        sc.read(bb);
                        String[] message = new String(bb.array()).trim().split(" ");
                        String type = message[0];
                        String id = message[1];

                        System.out.println("[Coordinator] customer " + id + " enters");

                        if (id.length() <= 0) {
                            sc.close();
                            System.out.println("Connection closed...");
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("[Coordinator] Error on Coordinator. " + e.getMessage());
        } finally {
            try {
                System.out.println("[Coordinator] Closing serverSocketChannel ");
                this.serverSocketChannel.close();
            } catch (IOException e) {
                System.out.println("[Coordinator] Error on close connection " + e.getMessage());
            }
        }
    }

    /**
     * Send broadcast message
     * 
     * @throws IOException
     */
    public void broadcast() throws IOException {
        System.out.println("[Coordinator] Broadcast");

        while (true) {
            try {
                String message = "coordinator " + this.id;
                SocketHelper.sendMessage(Constants.BARBER_HOST, Constants.BARBER_BRODCAST_LISTENER_PORT, message);

                for (int i = 0; i < this.lines.size(); i++) {
                    String[] data = this.lines.get(i).split(" ", 3);
                    int id = Integer.parseInt(data[0]);

                    if (id < this.id) {
                        SocketHelper.sendMessage(data[1], Integer.parseInt(data[2]), message);
                    }
                }

                TimeUnit.SECONDS.sleep(10);

            } catch (Exception e) {
                System.out.println("[Coordinator] error o broadcast " + e.getMessage());
            }
        }
    }

    public void listenBarber() throws IOException {
        System.out.println("[Coordinator] listenBarber");
        while (true) {
            try {
                Response res = SocketHelper.receiveMessage(Constants.BARBER_LISTENER_PORT, 0);
                System.out.println("[Coordinator] barber listener received: " + res.message);

                if (res.message.equals("up barber")) {
                    upBarber();
                    SocketHelper.sendMessage(this.host, Constants.BARBER_PORT, "ack");
                } else if (res.message.equals("down barber")) {
                    downBarber();
                    SocketHelper.sendMessage(this.host, Constants.BARBER_PORT, "ack");
                } else if (res.message.equals("up seats")) {
                    upSeats();
                    SocketHelper.sendMessage(this.host, Constants.BARBER_PORT, "ack");
                } else if (res.message.equals("down seats")) {
                    downSeats();
                    SocketHelper.sendMessage(this.host, Constants.BARBER_PORT, "ack");
                } else {
                    String[] msg = res.message.split(" ");
                    if (msg[0].equals("release")) {
                        // downCustomer();
                        SocketHelper.sendMessage(this.host, Constants.BARBER_PORT, "ack");
                    }
                }
            } catch (Exception e) {
                System.out.println("[Coordinator] error o listenBarber " + e.getMessage());
            }
        }
    }

    public void processList() throws IOException {
        System.out.println("[Coordinator] processList");
        while (true) {
            try {
                if (list.size() > 0) {
                    Customer c = list.get(0);
                    list.remove(0);
                    downCustomer();
                    SocketHelper.sendMessage(c.host, c.port, "granted");
                } else {
                    // System.out.println("[Coordinator] list size " + list.size());
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception e) {
                System.out.println("[Coordinator] error o processList " + e.getMessage());
            }
        }
    }

    public Customer getCustomerById(String id, SocketChannel sc) {
        List<String> lines = FileHelper.read("config.txt");

        for (String line : lines) {
            String[] data = line.split(" ", 3);
            if (data[0].equals(id)) {
                int customerId = Integer.parseInt(id);
                String host = data[1];
                int port = Integer.parseInt(data[2]);
                return new Customer(customerId, host, port, sc, this.host, this.port, null);
            }
        }
        return null;
    }

    public int getPortById(int id) {
        for (String line : this.lines) {
            String[] data = line.split(" ");
            if (Integer.parseInt(data[0]) == id)
                return Integer.parseInt(data[2]);
        }
        return 0;
    }

    public String getHostById(int id) {
        for (String line : this.lines) {
            String[] data = line.split(" ");
            if (Integer.parseInt(data[0]) == id)
                return data[1];
        }
        return null;
    }

    public void printList() {
        String msg = "| ";
        for (Customer c : this.list)
            msg += c.getId() + " |";

        System.out.println(msg);
    }

    public void removeById(int id) {
        for (int i = 0; i < this.list.size() - 1; i++) {
            if (this.list.get(i).getId() == id) {
                this.list.remove(i);
            }
        }
    }

}