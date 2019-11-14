import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.channels.*;

public class Storage {

    // number of customers waiting
    static int customers = 0;

    // seats available for hair cutting
    static int seats = 1;

    // barber is idle or working
    static int barber = 0;

    // Customers queue
    static ArrayList<Customer> list = new ArrayList<Customer>();

    static List<String> lines;

    static String coordinatorHost = "";
    static int coordinatorPort = 0;
    static String coordinatorId = "";

    public Storage() {

    }

    public static void upCustomer() {
        customers++;
    }

    public static void downCustomer() {
        customers--;
    }

    public static void upBarber() {
        barber++;
    }

    public static void downBarber() {
        barber--;
    }

    public static void upSeats() {
        seats++;
    }

    public static void downSeats() {
        seats--;
    }

    public static boolean addCustomerToQueue(Customer customer) {
        list.add(customer);
        return true;
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {

        // Broadcast thread
        Thread broadcastThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        String response = receiveBroadcast();
                        String id = response.split(" ", 2)[1];

                        if (id.equals(coordinatorId))
                            continue;

                        Coordinator coordinator = getCoordinatorById(Integer.parseInt(id));
                        coordinatorHost = coordinator.getHost();
                        coordinatorPort = coordinator.getPort();
                        coordinatorId = id;
                        System.out.println("[Storage] coordinator " + coordinatorHost + ":" + coordinatorPort);
                    }
                } catch (IOException e) {
                    System.out.println("[Storage] error on try broadcast " + e.getMessage());
                }
            }
        });
        broadcastThread.setName("broadcastThread");
        broadcastThread.start();

        while (true) {
            try {
                Response res = SocketHelper.receiveMessage(Constants.STORAGE_PORT, 0);
                String command = res.message;
                System.out.println("[Storage] received:" + command);

                if (command.equals("up barber")) {
                    upBarber();

                } else if (command.equals("down barber")) {
                    downBarber();

                } else if (command.equals("up seats")) {
                    upSeats();

                } else if (command.equals("down seats")) {
                    downSeats();

                } else if (command.equals("up costumer")) {
                    upCustomer();

                } else if (command.equals("down costumer")) {
                    downCustomer();

                } else if (command.equals("get")) {
                    String msg = barber + " " + seats + " " + customers;
                    SocketHelper.sendMessage(coordinatorHost, coordinatorPort, msg);

                } else {
                    String[] msg = res.message.split(" ");
                    if (msg[0].equals("release")) {
                        String id = msg[1];
                    }
                }
            } catch (Exception e) {
                System.out.println("[Barber] error on loop " + e.getMessage());
            }
        }
    }

    /**
     * Receive broadcast message from coordinator
     * 
     * @return String message
     * @throws IOException
     */
    public static String receiveBroadcast() throws IOException {
        // System.out.println("[Barber] receiveBroadcast ");
        try {
            Response response = SocketHelper.receiveMessage(Constants.STORAGE_BRODCAST_LISTENER_PORT, 0);
            // System.out.println("[Barber] Broadcast message received: " +
            // response.message);
            return response.message;
        } catch (Exception e) {
            System.out.println("[Barber] Error on receiveBroadcast. " + e.getMessage());
            return null;
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
                return new Customer(customerId, host, port, sc, null, 0, null);
            }
        }
        return null;
    }

    public static Coordinator getCoordinatorById(int id) {
        List<String> lines = FileHelper.read("config.txt");

        for (String line : lines) {
            String[] data = line.split(" ", 3);
            if (Integer.parseInt(data[0]) == id)
                return new Coordinator(id, data[1], Integer.parseInt(data[2]), null);
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