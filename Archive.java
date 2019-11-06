import java.util.List;
import java.io.IOException;

public class Archive {

    private static final String FILENAME = "shared.txt";

    public static void main(String[] args) {
        while (true) {
            try {
                Response response = SocketHelper.receiveMessage(Constants.ARCHIVE_PORT, 0);
                String[] message = response.message.split(" ", 3);

                int id = Integer.parseInt(message[0]);
                String command = message[1];

                System.out.println("[Archive] " + response.hostname + " request " + command);

                if (command.equals("write")) {
                    String content = message[2];
                    FileHelper.write(FILENAME, content);
                    SocketHelper.sendMessage(response.hostname, Constants.MESSAGE_PORT, "ack");
                } else {
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