import java.io.IOException;
import java.util.List;

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

        System.out.println("[Barber] host " + host + ":" + port);

        // Broadcast thread
        Thread broadcastThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        String response = receiveBroadcast();
                        String id = response.split(" ", 2)[1];
                        Coordinator coordinator = getCoordinatorById(Integer.parseInt(id));
                        coordinatorHost = coordinator.getHost();
                        coordinatorPort = coordinator.getPort();
                        host = coordinatorHost;
                        System.out.println("[Barber] coordinator " + coordinatorHost + ":" + coordinatorPort);
                    }
                } catch (IOException e) {
                    System.out.println("[Coordinator] error on try broadcast " + e.getMessage());
                }
            }
        });
        broadcastThread.setName("broadcastThread");
        broadcastThread.start();

        boolean restart = false;
        while (true) {

            try {
                Response res = SocketHelper.receiveMessage(port, Constants.TIMOUT);

                String[] split = res.message.split(" ");

                if (!split[0].equals("acquier"))
                    continue;

                String id = split[1];

                System.out.println("[Barber] Customer " + id + " acquire lock");

                restart = sendMessage("up barber");
                if (restart)
                    continue;

                restart = sendMessage("down seats");
                if (restart)
                    continue;

                restart = sendMessage("down barber");
                if (restart)
                    continue;

                restart = sendMessage("up seats");
                if (restart)
                    continue;

                System.out.println("[Barber] Customer " + id + " release lock");

                restart = sendMessage("release " + id);
                if (restart)
                    continue;

            } catch (IOException e) {
                System.out.println("[Barber] IOException " + e.getMessage());
                continue;
            } catch (Exception e) {
                System.out.println("[Barber] error on loop " + e.getMessage());
            }
        }
    }

    public static boolean sendMessage(String msg) {
        try {
            SocketHelper.sendMessage(coordinatorHost, Constants.BARBER_LISTENER_PORT, msg);
            SocketHelper.receiveMessage(Constants.BARBER_PORT, Constants.TIMOUT);
            return false;
        } catch (IOException e) {
            System.out.println("[Barber] IOException " + e.getMessage());
            // coordinatorHost = null;
            return true;
        }
    }

    /**
     * Receive broadcast message from coordinator
     * 
     * @return String message
     * @throws IOException
     */
    public static String receiveBroadcast() throws IOException {
        System.out.println("[Barber] receiveBroadcast ");
        try {
            Response response = SocketHelper.receiveMessage(Constants.BARBER_BRODCAST_LISTENER_PORT, 0);
            System.out.println("[Barber] Broadcast message received: " + response.message);
            return response.message;
        } catch (Exception e) {
            System.out.println("[Barber] Error on receiveBroadcast. " + e.getMessage());
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