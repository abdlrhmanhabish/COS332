import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FtpBackupClient {

    private static final String CRLF = "\r\n";
    private static final int POLL_INTERVAL_MS = 60_000;

    private static class FtpReply {
        final int code;
        final String message;

        FtpReply(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private static class FtpSession implements Closeable {
        private final Socket controlSocket;
        private final BufferedReader controlReader;
        private final OutputStream controlWriter;

        FtpSession(String host, int port, String username, String password, String remoteDirectory) throws IOException {
            this.controlSocket = new Socket(host, port);
            this.controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.US_ASCII));
            this.controlWriter = controlSocket.getOutputStream();

            FtpReply greeting = readReply(controlReader);
            // Check if FTP greeting code is 220 (ready)
            if (greeting.code != 220) throw new IOException("Expected FTP greeting 220, got " + greeting.code + ": " + greeting.message);

            sendCommand("USER " + username);
            FtpReply userReply = readReply(controlReader);
            // Check if password is needed (331 means password required)
            if (userReply.code == 331) {
                sendCommand("PASS " + password);
                FtpReply passReply = readReply(controlReader);
                // Check if password authentication was successful (230 means user logged in)
                if (passReply.code != 230) throw new IOException("expected 230 after PASS, got " + passReply.code + ": " + passReply.message);
            } 
            // Check if user is already logged in without password (230) or invalid response
            else if (userReply.code != 230) {
                throw new IOException("expected 230 or 331 after USER, got " + userReply.code + ": " + userReply.message);
            }

            sendCommand("CWD " + remoteDirectory);
            FtpReply cwdReply = readReply(controlReader);
            // check if directory chnage was not succesful
            if (cwdReply.code != 250) throw new IOException("CWD failed: expected 250, got " + cwdReply.code + ": " + cwdReply.message);

            sendCommand("TYPE I");
            FtpReply typeReply = readReply(controlReader);
            // check if binary mode was not correct
            if (typeReply.code != 200) throw new IOException("TYPE I failed: expected 200, got " + typeReply.code + ": " + typeReply.message);
        }

        void upload(File localFile, String remoteFileName) throws IOException {
            try (ServerSocket activeSocket = new ServerSocket(0)) {
                InetAddress localAddress = resolveLocalIPv4Address(controlSocket);
                String portCommand = buildPortCommand(localAddress, activeSocket.getLocalPort());

                sendCommand(portCommand);
                FtpReply portReply = readReply(controlReader);
                // Check if PORT command was successful (200 means command okay)
                if (portReply.code != 200) throw new IOException("FTP PORT failed: expected 200, got " + portReply.code + ": "+ portReply.message);

                sendCommand("STOR " + remoteFileName);
                FtpReply storReply = readReply(controlReader);
                // Check if STOR command was accepted (150 or 125 means data connection opening)
                if (storReply.code != 150 && storReply.code != 125) throw new IOException("STOR failed: expected 150 or 125, got " + storReply.code + ": " + storReply.message);

                try (Socket dataSocket = activeSocket.accept();
                        InputStream fileInput = new FileInputStream(localFile);
                        OutputStream dataOutput = dataSocket.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    // Loop through file data until end of file 
                    while ((read = fileInput.read(buffer)) != -1) dataOutput.write(buffer, 0, read);

                    dataOutput.flush();
                }

                FtpReply completionReply = readReply(controlReader);
                // Check if file transfer completed successfully (226 means closing data connection)
                if (completionReply.code != 226) throw new IOException("transfer nvr work properly: expected 226, got " + completionReply.code + ": " + completionReply.message);
            }
        }

        private void sendCommand(String command) throws IOException {
            controlWriter.write((command + CRLF).getBytes(StandardCharsets.US_ASCII));
            controlWriter.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                // Check if socket is still connected and open
                if (controlSocket.isConnected() && !controlSocket.isClosed()) {
                    try {
                        sendCommand("QUIT");
                        readReply(controlReader);
                    } 
                    catch (IOException ignored) {}
                }
            } 
            finally {
                controlSocket.close();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Usage: java FtpBackupClient <ftpHost> <ftpPort> <username> <password> <localDirectory> <remoteBackupDirectory>");
            return;
        }

        String ftpHost = args[0];
        int ftpPort = Integer.parseInt(args[1]);
        String username = args[2];
        String password = args[3];
        File localDirectory = new File(args[4]);
        String remoteBackupDirectory = args[5];

        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            System.err.println("Local directory does not exist or is not a directory: " + localDirectory.getAbsolutePath());
            return;
        }

        // Track the last observed modification time for each .txt file.
        Map<String, Long> lastModifiedByFile = new HashMap<>();

        try (FtpSession session = new FtpSession(ftpHost, ftpPort, username, password, remoteBackupDirectory)) {
            System.out.println("Connected to server: " + localDirectory.getAbsolutePath());

            // Main polling loop to continuously monitor for file changes
            while (true) {
                // Snapshot the current .txt files in the directory on each poll.
                File[] files = localDirectory.listFiles();
                Map<String, File> currentTxtFiles = new HashMap<>();

                if (files != null) {
                    for (File file : files) {
                        // Check if item is a file and has .txt extension
                        if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) currentTxtFiles.put(file.getName(), file);
                    }
                }

                for (Map.Entry<String, File> entry : currentTxtFiles.entrySet()) {
                    String fileName = entry.getKey();
                    File file = entry.getValue();
                    long currentLastModified = file.lastModified();
                    Long previousLastModified = lastModifiedByFile.get(fileName);

                    // Check if file is new or has been modified since last check
                    if (previousLastModified == null || previousLastModified.longValue() != currentLastModified) {
                        String remoteFileName = fileName;

                        System.out.println("Saw change in " + fileName + ", uploading as " + remoteFileName + "...");
                        try {
                            session.upload(file, remoteFileName);
                            lastModifiedByFile.put(fileName, currentLastModified);
                            System.out.println("Upload complete: " + remoteFileName);
                        } 
                        catch (IOException uploadError) {
                            System.err.println("Upload failed for " + fileName + " -> " + remoteFileName + ": "+ uploadError.getMessage());
                        }
                    }
                }

                // If a file disappears locally, just forget its old timestamp.
                lastModifiedByFile.keySet().removeIf(name -> !currentTxtFiles.containsKey(name));

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } 
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    System.out.println("Stopped checking.");
                    return;
                }
            }
        } 
        catch (IOException exception) {
            System.err.println("FTP client error: " + exception.getMessage());
            exception.printStackTrace(System.err);
        }
    }

    private static FtpReply readReply(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) throw new IOException("FTP server closed unexpectedly.");

        StringBuilder message = new StringBuilder(firstLine);
        // Check if this is a multi-line FTP reply (format: xxx- where x is digit)
        if (firstLine.length() >= 4 && Character.isDigit(firstLine.charAt(0)) && Character.isDigit(firstLine.charAt(1)) && 
        Character.isDigit(firstLine.charAt(2)) && firstLine.charAt(3) == '-') {

            String replyCode = firstLine.substring(0, 3);
            // Loop through remaining lines of multiline reply until end marker is found
            while (true) {
                String line = reader.readLine();
                // Check if reply was truncated 
                if (line == null) throw new IOException("FTP multiline reply was truncated.");
                message.append("\n").append(line);
                // Check if this is the last line of multiline reply (format: xxx )
                if (line.length() >= 4 && line.startsWith(replyCode) && line.charAt(3) == ' ') break;
            }
        }

        if (firstLine.length() < 3) throw new IOException("Malformed FTP reply: " + firstLine);

        int code;
        try {
            code = Integer.parseInt(firstLine.substring(0, 3));
        } 
        catch (NumberFormatException exception) {
            throw new IOException("Corrupted FTP reply code: " + firstLine, exception);
        }

        return new FtpReply(code, message.toString());
    }

    private static String buildPortCommand(InetAddress address, int port) {
        String[] octets = address.getHostAddress().split("\\.");
        if (octets.length != 4) throw new IllegalArgumentException("We requires an IPv4 address, got: " + address.getHostAddress());

        int p1 = port / 256;
        int p2 = port % 256;
        return "PORT " + octets[0] + "," + octets[1] + "," + octets[2] + "," + octets[3] + "," + p1 + "," + p2;
    }

    private static InetAddress resolveLocalIPv4Address(Socket controlSocket) throws IOException {
        InetAddress socketAddress = controlSocket.getLocalAddress();
        // Check if socket's local address is a valid IPv4 address (not any address)
        if (socketAddress instanceof Inet4Address && !socketAddress.isAnyLocalAddress()) return socketAddress;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            // Loop through all available network interfaces
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                // Loop through all addresses on this network interface
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) return address;
                }
            }
        } 
        catch (Exception exception) {
            throw new IOException("Can't get a local IPv4 address for FTP PORT.", exception);
        }

        InetAddress localhost = InetAddress.getLocalHost();
        if (localhost instanceof Inet4Address) return localhost;

        throw new IOException("Can't get a usable IPv4 address for FTP PORT.");
    }

}