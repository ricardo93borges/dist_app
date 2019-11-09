public class Response {
    String message;
    String hostname;
    int port;

    public Response(String message, String hostname, int port) {
        this.message = message;
        this.hostname = hostname;
        this.port = port;
    }
}