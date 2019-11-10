import java.nio.channels.SocketChannel;

class Customer {
    int id;
    String host;
    int port;
    SocketChannel sc;

    public Customer(int id, String host, int port, SocketChannel sc) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.sc = sc;
    }
}