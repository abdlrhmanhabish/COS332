import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class server {

    private static final AtomicInteger SENDER_COUNTER = new AtomicInteger(1);

    private static int envIntOrDefault(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) return fallback;

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String envStringOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private static int parseTopMessageNumber(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return 1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static void main(String[] args) {
        final int mockMessageCount = Math.max(0, envIntOrDefault("MOCK_POP3_COUNT", 1));
        final String mockSubject = envStringOrDefault("MOCK_POP3_SUBJECT", "prac7");
        final String senderMode = envStringOrDefault("MOCK_POP3_SENDER_MODE", "unique").toLowerCase();

        // Start POP3 Dummy Server on Port 1110
        new Thread(() -> {
            try (ServerSocket popServer = new ServerSocket(1110)) {
                System.out.println("[POP3] Listening on port 1110...");
                while (true) {
                    try (Socket client = popServer.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                         BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()))) {
                        
                        out.write("+OK POP3 server ready\r\n");
                        out.flush();

                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("USER") || line.startsWith("PASS")) {
                                out.write("+OK\r\n");
                            } else if (line.startsWith("STAT")) {
                                out.write("+OK " + mockMessageCount + " 250\r\n");
                            } else if (line.startsWith("TOP")) {
                                int messageNumber = parseTopMessageNumber(line);
                                int senderId = SENDER_COUNTER.getAndIncrement();
                                String sender;
                                if ("fixed".equals(senderMode)) {
                                    sender = "marker@university.edu";
                                } else {
                                    sender = "marker" + senderId + "@university.edu";
                                }

                                out.write("+OK\r\n");
                                out.write("From: <" + sender + ">\r\n");
                                out.write("Subject: " + mockSubject + "\r\n");
                                out.write("X-Mock-Message-Number: " + messageNumber + "\r\n");
                                out.write(".\r\n"); // End of TOP output
                            } else if (line.startsWith("QUIT")) {
                                out.write("+OK Bye\r\n");
                                out.flush();
                                break;
                            }
                            out.flush();
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        // Start SMTP Dummy Server on Port 1025
        new Thread(() -> {
            try (ServerSocket smtpServer = new ServerSocket(1025)) {
                System.out.println("[SMTP] Listening on port 1025...");
                while (true) {
                    try (Socket client = smtpServer.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                         BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()))) {
                        
                        out.write("220 Mock SMTP Ready\r\n");
                        out.flush();

                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("DATA")) {
                                out.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                                out.flush();
                                System.out.println("\n--- [SMTP] RECEIVED AUTO-REPLY ---");
                                while ((line = in.readLine()) != null) {
                                    System.out.println(line);
                                    if (line.equals(".")) break;
                                }
                                System.out.println("----------------------------------\n");
                                out.write("250 OK\r\n");
                            } else if (line.startsWith("QUIT")) {
                                out.write("221 Bye\r\n");
                                out.flush();
                                break;
                            } else {
                                out.write("250 OK\r\n"); 
                            }
                            out.flush();
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}