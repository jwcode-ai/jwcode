import java.io.*;
public class TestPs2 {
    public static void main(String[] args) throws Exception {
        String[] cmd = {"powershell", "-NoProfile", "-Command",
            "for (\ = 1; \ -le 150; \++) { 'file{0}.txt' -f \ }"};
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();

        long start = System.currentTimeMillis();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = r.readLine()) != null) {
                count++;
            }
            System.out.println("Lines: " + count);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("ERR: " + line);
            }
        }
        int code = p.waitFor();
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("EXIT: " + code + " in " + elapsed + "ms");
    }
}