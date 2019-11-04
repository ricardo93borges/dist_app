import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileHelper {

    public static List<String> read(String filename) {
        List<String> result;
        try (Stream<String> lines = Files.lines(Paths.get(filename))) {
            result = lines.collect(Collectors.toList());
            return result;
        } catch (IOException e) {
            System.out.println("Error on read file. " + e.getMessage());
            return null;
        }
    }

    public static void write(String filename, String content) {
        try (FileWriter writer = new FileWriter(filename, true); BufferedWriter bw = new BufferedWriter(writer)) {
            bw.write(content);
            bw.write("\n");
        } catch (IOException e) {
            System.err.format("IOException: %s%n", e);
        }
    }

}