import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MockMailServer {

    public static void main(String[] args) {
        System.out.println("Starting Mock Mail Servers for Testing...");

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
                                out.write("+OK 1 250\r\n"); // 1 dummy email
                            } else if (line.startsWith("TOP")) {
                                out.write("+OK\r\n");
                                out.write("From: <marker@university.edu>\r\n");
                                out.write("Subject: prac7\r\n");
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
                                // Read the email body until the single dot
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
                                out.write("250 OK\r\n"); // Generic success for HELO, MAIL FROM, RCPT TO
                            }
                            out.flush();
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}