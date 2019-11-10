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
        this.port = this.getPortById(id);
        this.host = this.getHostById(id);
    }

    // public boolean run() {
    // try {
    // // Tell the other I'm the coordinator
    // this.broadcast();
    // } catch (IOException e) {
    // System.out.println("Error on Coordinator. " + e.getMessage());
    // }
    //
    // while (true) {
    // try {
    // // Wait for requests
    // this.receiveRequests();
    //
    // } catch (Exception e) {
    // System.out.println("Error on Coordintor. " + e.getMessage());
    // break;
    // }
    // }
    //
    // return false;
    // }

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

    public Boolean run() {

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

                    String msg = "done";
                    ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
                    customer.sc.write(bb);
                    bb.clear();

                    this.customers--;
                    this.barber--;
                    this.seats++;
                }

                // System.out.println("barber: " + this.barber);
                // System.out.println("seats: " + this.seats);
                // System.out.println("customers: " + this.customers);

                TimeUnit.SECONDS.sleep(2);
            }
        } catch (Exception e) {
            System.out.println("[Coordinator] error on process. " + e.getMessage());
        }

    }

    public void handleConnections() {
        System.out.println("[Coordinator] handleConnections");
        try {
            // InetAddress host = InetAddress.getByName("localhost");
            Selector selector = Selector.open();

            System.out.println("host: " + this.host);
            System.out.println("port: " + this.port);

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
                        System.out.println("Connected: " + sc.getLocalAddress() + "\n");
                    }

                    if (key.isWritable()) {
                        if (this.notifyQueue.size() > 0) {
                            System.out.println("notify");
                            Customer customer = notifyQueue.poll();
                            String msg = "done";
                            // SocketChannel sc = (SocketChannel) key.channel();
                            ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
                            customer.sc.write(bb);
                        }
                    }

                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer bb = ByteBuffer.allocate(1024);
                        sc.read(bb);
                        String id = new String(bb.array()).trim();

                        System.out.println("Received: " + id);

                        if (customers == MAX_CHAIRS || this.list.size() == MAX_CHAIRS) {
                            String msg = "full";
                            bb = ByteBuffer.wrap(msg.getBytes());
                            sc.write(bb);
                            bb.clear();
                        } else {
                            Customer customer = this.getCustomerById(id, sc);
                            if (addCustomerToQueue(customer)) {
                                upCustomer();
                            }
                            /*
                             * String msg = "wait"; bb = ByteBuffer.wrap(msg.getBytes()); sc.write(bb);
                             * bb.clear();
                             */
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

    /**
     * Receive messages
     * 
     * @throws IOException
     */
    /*
     * public void receiveRequests() throws IOException {
     * System.out.println("[Coordinator] Ready to receive requests"); try { while
     * (true) { if (this.customers > 0 && this.seats == 1) {
     * 
     * this.barber++; this.seats--;
     * 
     * Response response = this.queue.poll(); int port =
     * this.getPortById(response.message);
     * SocketHelper.sendMessage(response.hostname, port, "done");
     * 
     * this.customers--; this.barber--; this.seats++; }
     * 
     * Response response = SocketHelper.receiveMessage(Constants.COORD_PORT, 0);
     * System.out.println("Received: " + response.message);
     * 
     * if (this.customers == Constants.MAX_CHAIRS) {
     * SocketHelper.sendMessage(response.hostname, response.port, "denied"); } else
     * { this.customers++; this.queue.add(response); }
     * 
     * // System.out.println("barber: " + this.barber); //
     * System.out.println("seats: " + this.seats); //
     * System.out.println("customers: " + this.customers); }
     * 
     * } catch (Exception e) {
     * System.out.println("[Coordinator] Error on receiveRequests. " +
     * e.getMessage()); } }
     */

    public Customer getCustomerById(String id, SocketChannel sc) {
        List<String> lines = FileHelper.read("config.txt");

        for (String line : lines) {
            String[] data = line.split(" ", 3);
            if (data[0].equals(id)) {
                int customerId = Integer.parseInt(id);
                String host = data[1];
                int port = Integer.parseInt(data[2]);
                return new Customer(customerId, host, port, sc);
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

}