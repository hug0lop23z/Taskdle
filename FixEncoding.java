import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

public class FixEncoding {
    public static void main(String[] args) throws Exception {
        Path p = Paths.get("d:/ia_proy/web/index.html");
        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        byte[] originalBytes = content.getBytes(Charset.forName("windows-1252"));
        String restored = new String(originalBytes, StandardCharsets.UTF_8);
        Files.write(p, restored.getBytes(StandardCharsets.UTF_8));
        System.out.println("Done!");
    }
}
