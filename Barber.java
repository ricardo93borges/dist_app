import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

class Barber {
    static String host;
    static int port;
    static String coordinatorHost;
    static int coordinatorPort;

    public static void main(String[] args) {
        host = null;
        port = Constants.BARBER_PORT;
        coordinatorHost = null;
        coordinatorPort = 0;

        run();
    }

    public static int run() {
        if (coordinatorHost == null) {
            // wait for broadcast message from coordinator
            try {
                String response = receiveBroadcast();
                String id = response.split(" ", 2)[1];
                Coordinator coordinator = getCoordinatorById(Integer.parseInt(id));
                coordinatorHost = coordinator.getHost();
                coordinatorPort = coordinator.getPort();
                host = coordinatorHost;
            } catch (IOException e) {
                System.out.println("[Baber] Error on barber " + e.getMessage());
            }
        }

        System.out.println("[Baber] host " + host + ":" + port);
        System.out.println("[Baber] coordinator " + coordinatorHost + ":" + coordinatorPort);

        while (true) {
            try {
                Response res = SocketHelper.receiveMessage(port, 0);

                System.out.println("[Barber] Customer " + res.message + " acquire lock");

                SocketHelper.sendMessage(coordinatorHost, Constants.BARBER_LISTENER_PORT, "up barber");
                SocketHelper.receiveMessage(Constants.BARBER_PORT, 0);

                SocketHelper.sendMessage(coordinatorHost, Constants.BARBER_LISTENER_PORT, "down seats");
                SocketHelper.receiveMessage(Constants.BARBER_PORT, 0);

                TimeUnit.SECONDS.sleep(3);

                SocketHelper.sendMessage(coordinatorHost, Constants.BARBER_LISTENER_PORT, "down barber");
                SocketHelper.receiveMessage(Constants.BARBER_PORT, 0);

                SocketHelper.sendMessage(coordinatorHost, Constants.BARBER_LISTENER_PORT, "up seats");
                SocketHelper.receiveMessage(Constants.BARBER_PORT, 0);

                System.out.println("[Barber] Customer " + res.message + " release lock");

                SocketHelper.sendMessage(coordinatorHost, Constants.BARBER_LISTENER_PORT, "release " + res.message);
                SocketHelper.receiveMessage(Constants.BARBER_PORT, 0);

            } catch (Exception e) {
                System.out.println("[Baber] error on loop " + e.getMessage());
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
        System.out.println("[Customer] receiveBroadcast ");
        try {
            Response response = SocketHelper.receiveMessage(port, 0);
            System.out.println("[Customer] Broadcast message received: " + response.message);
            return response.message;
        } catch (Exception e) {
            System.out.println("[Customer] Error on receiveBroadcast. " + e.getMessage());
            return null;
        }
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
}