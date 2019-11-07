import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Coordinator {

    // boolean writing = false;

    AtomicInteger readers = new AtomicInteger(0);
    AtomicInteger writers = new AtomicInteger(0);
    int mutex = 1;

    int id;
    List<String> lines;

    public Coordinator(int id, List<String> lines) {
        this.id = id;
        this.lines = lines;
    }

    public boolean run() {

        try {
            // Tell the other I'm the coordinator
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

        // Get hosts with IDs less than mine
        ArrayList<String> hosts = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String[] data = lines.get(i).split(" ", 3);
            int id = Integer.parseInt(data[0]);
            if (id < this.id)
                hosts.add(data[1]);
        }

        for (int i = 0; i < hosts.size(); i++) {
            // Send election message
            String message = "coordinator " + this.id;
            String host = hosts.get(i);
            System.out.println("[Coordinator] send message to: " + host);
            SocketHelper.sendMessage(host, Constants.BROADCAST_PORT, message);
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
                Response response = SocketHelper.receiveMessage(Constants.COORD_PORT, 0);
                String[] splitted = response.message.split(" ");
                String message = splitted[0];
                String receivedToken = null;

                if (splitted.length > 1) {
                    receivedToken = splitted[1];
                }

                System.out.println("> host " + response.hostname + " request " + response.message);
                // System.out.println("> mutex = " + this.mutex);
                System.out.println("> readers = " + this.readers);
                System.out.println("> writers = " + this.writers);

                if (message.equals("write")) {
                    if (this.writers.get() == 1) {
                        if (receivedToken == null) {
                            SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "denied");
                        } else {
                            if (this.readers.get() == 0) {
                                SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "granted");
                            } else {
                                SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT,
                                        "token " + receivedToken);
                            }
                        }
                    } else {
                        this.writers.incrementAndGet();
                        // this.mutex--;

                        if (this.readers.get() == 0) {
                            SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "granted");
                        } else {
                            String token = StringGenerator.generate();
                            SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "token " + token);
                        }
                    }
                } else if (message.equals("read")) {
                    if (this.writers.get() == 0) {
                        this.readers.incrementAndGet();
                        SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "granted");
                    } else {
                        SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "denied");
                    }
                } else if (response.message.equals("release read")) {
                    this.readers.decrementAndGet();
                } else if (response.message.equals("release write")) {
                    // this.mutex++;
                    this.writers.decrementAndGet();
                }

                // System.out.println("> mutex = " + this.mutex);
                System.out.println("> readers = " + this.readers);
                System.out.println("> writers = " + this.writers);
                System.out.println(" ------------------------- ");
            }

        } catch (Exception e) {
            System.out.println("[Coordinator] Error on receiveRequests. " + e.getMessage());
        }
    }

}