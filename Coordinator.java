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

        Thread broadcastThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // tell the other I'm the coordinator
                    broadcast();
                } catch (IOException e) {
                    System.out.println("[Coordinator] error on try broadcast " + e.getMessage());
                }
            }
        });
        broadcastThread.setName("broadcastThread");
        broadcastThread.start();

        Thread connectionsHandler = new Thread(new Runnable() {
            @Override
            public void run() {
                handleConnections();
            }
        });
        connectionsHandler.setName("connectionsHandler");
        connectionsHandler.start();

        this.process();

        return false;
    }

    public void process() {
        try {
            while (true) {
                if (this.customers > 0 && this.seats == 1) {
                    this.barber++;
                    this.seats--;

                    Customer customer = this.list.get(0);
                    list.remove(0);

                    System.out.println("[Coordinator] barber cutting customer " + customer.id + " hair");

                    String msg = "done";
                    ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
                    customer.sc.write(bb);
                    bb.clear();

                } else {
                    System.out.println("[Coordinator] barber sleeping");
                    TimeUnit.SECONDS.sleep(1);
                }

            }
        } catch (Exception e) {
            System.out.println("[Coordinator] error on process. " + e.getMessage());
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

                    if (key.isWritable()) {
                        if (this.notifyQueue.size() > 0) {
                            System.out.println("notify");
                            Customer customer = notifyQueue.poll();
                            String msg = "done";
                            ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
                            customer.sc.write(bb);
                        }
                    }

                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer bb = ByteBuffer.allocate(1024);
                        sc.read(bb);
                        String[] message = new String(bb.array()).trim().split(" ");
                        String type = message[0];
                        String id = message[1];

                        System.out.println("[Coordinator] customer " + id + " enters");

                        if (type.equals("acquire")) {
                            if (customers == MAX_CHAIRS || this.list.size() == MAX_CHAIRS) {
                                System.out.println("[Coordinator] waiting room is full, come back later");
                                /*
                                 * String msg = "full"; bb = ByteBuffer.wrap(msg.getBytes()); sc.write(bb);
                                 * bb.clear();
                                 */
                            } else {
                                System.out.println("[Coordinator] go to waiting room ");
                                Customer customer = this.getCustomerById(id, sc);
                                if (addCustomerToQueue(customer)) {
                                    upCustomer();
                                }
                            }
                        } else {
                            // this.removeById(Integer.parseInt(id));
                            this.customers--;
                            this.barber--;
                            this.seats++;
                        }

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
            for (int i = 0; i < this.lines.size(); i++) {
                String[] data = this.lines.get(i).split(" ", 3);
                int id = Integer.parseInt(data[0]);

                if (id < this.id) {
                    String message = "coordinator " + this.id;
                    SocketHelper.sendMessage(data[1], Integer.parseInt(data[2]), message);
                }
            }

            try {
                TimeUnit.SECONDS.sleep(15);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
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