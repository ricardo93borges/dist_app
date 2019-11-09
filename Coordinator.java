import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class Coordinator {

    // boolean writing = false;

    // number of customers waiting
    int customers = 0;

    // seats available for hair cutting
    int seats = 1;

    // barber is idle or working
    int barber = 0;

    // Customers queue
    ArrayBlockingQueue<Response> queue = new ArrayBlockingQueue<Response>(Constants.MAX_CHAIRS);

    int id;
    int port;

    public Coordinator(int id) {
        this.id = id;
        this.port = 0;
    }

    public boolean run() {
        try {
            // Tell the other I'm the coordnator
            this.broadcast();
        } catch (IOException e) {
            System.out.println("Error on Coordinator. " + e.getMessage());
        }

        while (true) {
            try {
                // Wait for requests
                this.receiveRequests();

            } catch (Exception e) {
                System.out.println("Error on Coordintor. " + e.getMessage());
                break;
            }
        }

        return false;
    }

    /**
     * Send broadcast message
     * 
     * @throws IOException
     */
    public void broadcast() throws IOException {
        System.out.println("[Coordinator] Broadcast");
        InetAddress address = InetAddress.getByName(Constants.BROADCAST_HOST);
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);

        try {
            String broadcastMessage = "coordinator " + this.id;
            byte[] buffer = broadcastMessage.getBytes();

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, Constants.BROADCAST_PORT);
            socket.send(packet);
            socket.close();

        } catch (Exception e) {
            System.out.println("Error on broadcast. " + e.getMessage());
            socket.close();
        }
    }

    /**
     * Receive messages
     * 
     * @throws IOException
     */
    public void receiveRequests() throws IOException {
        System.out.println("[Coordinator] Ready to receive requests");
        try {
            while (true) {
                if (this.customers > 0 && this.seats == 1) {

                    this.barber++;
                    this.seats--;

                    Response response = this.queue.poll();
                    int port = this.getPortById(response.message);
                    SocketHelper.sendMessage(response.hostname, port, "done");

                    this.customers--;
                    this.barber--;
                    this.seats++;
                }

                Response response = SocketHelper.receiveMessage(Constants.COORD_PORT, 0);
                System.out.println("Received: " + response.message);

                if (this.customers == Constants.MAX_CHAIRS) {
                    SocketHelper.sendMessage(response.hostname, response.port, "denied");
                } else {
                    this.customers++;
                    this.queue.add(response);
                }

                // System.out.println("barber: " + this.barber);
                // System.out.println("seats: " + this.seats);
                // System.out.println("customers: " + this.customers);
            }

        } catch (Exception e) {
            System.out.println("[Coordinator] Error on receiveRequests. " + e.getMessage());
        }
    }

    public int getPortById(String id) {
        List<String> lines = FileHelper.read("config.txt");

        for (String line : lines) {
            String[] data = line.split(" ", 3);
            if (data[0].equals(id))
                return Integer.parseInt(data[2]);
        }
        return 0;
    }

}