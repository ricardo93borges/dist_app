import java.util.List;
import java.io.IOException;

public class Archive {

    private static final String FILENAME = "shared.txt";

    public static void main(String[] args) {
        while (true) {
            try {
                Response response = SocketHelper.receiveMessage(Constants.ARCHIVE_PORT, 0);
                System.out.println("[Archive] receive: " + response.message);
                String[] message = response.message.split(" ", 2);

                System.out.println("message: " + message[0]);
                if (message[0].equals("write")) {
                    FileHelper.write(FILENAME, message[1]);
                } else {
                    System.out.println("read ");
                    List<String> lines = FileHelper.read(FILENAME);
                    String line = lines.get(lines.size() - 1);
                    SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, line);
                }

            } catch (IOException e) {
                System.out.println("[Archive] " + e.getMessage());
            }
        }
    }
}