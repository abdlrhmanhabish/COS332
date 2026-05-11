//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

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
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpBackupClient {

    private static final String CRLF = "\r\n";
    private static final int POLL_INTERVAL_MS = 10_000;
    private static final Pattern PASV_PATTERN = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)");

    private static class FtpReply {
        final int code;
        final String message;

        FtpReply(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private static class PasvEndpoint {
        final String host;
        final int port;

        PasvEndpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static class FtpSession implements Closeable {
        private final Socket controlSocket;
        private final BufferedReader controlReader;
        private final OutputStream controlWriter;
        private final boolean passive;

        FtpSession(String host, int port, String username, String password, String remoteDirectory, boolean passive) throws IOException {
            this.controlSocket = new Socket(host, port);
            this.controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.US_ASCII));
            this.controlWriter = controlSocket.getOutputStream();
            this.passive = passive;

            FtpReply greeting = readReply(controlReader);
            // check if FTP greeting code is 220 (ready)
            if (greeting.code != 220)
                throw new IOException("Expected FTP greeting 220, got " + greeting.code + ": " + greeting.message);

            sendCommand("USER " + username);
            FtpReply userReply = readReply(controlReader);
            // check if password is needed (331 means password required)
            if (userReply.code == 331) {
                sendCommand("PASS " + password);
                FtpReply passReply = readReply(controlReader);
                // check if password authentication was successful (230 means user logged in)
                if (passReply.code != 230)
                    throw new IOException("expected 230 after PASS, got " + passReply.code + ": " + passReply.message);
            }
            // check if user is already logged in without password (230) or invalid response
            else if (userReply.code != 230) {
                throw new IOException(
                        "expected 230 or 331 after USER, got " + userReply.code + ": " + userReply.message);
            }

            sendOptionalCommand("SYST");
            sendOptionalCommand("FEAT");
            sendOptionalCommand("MODE S");
            sendOptionalCommand("STRU F");

            ensureRemoteDirectory(remoteDirectory);

            sendCommand("TYPE I");
            FtpReply typeReply = readReply(controlReader);
            // check if binary mode was not correct
            if (typeReply.code != 200)
                throw new IOException("TYPE I failed: expected 200, got " + typeReply.code + ": " + typeReply.message);
        }

        void upload(File localFile, String remoteFileName) throws IOException {
            if (passive) {
                PasvEndpoint endpoint = enterPassiveMode();
                try (Socket dataSocket = new Socket()) {
                    dataSocket.connect(new InetSocketAddress(endpoint.host, endpoint.port), 10_000);

                    sendCommand("STOR " + remoteFileName);
                    FtpReply storReply = readReply(controlReader);
                    // check if STOR command was accepted (150 or 125 means data connection opening)
                    if (storReply.code != 150 && storReply.code != 125)
                        throw new IOException("STOR failed: expected 150 or 125, got " + storReply.code + ": " + storReply.message);

                    try (InputStream fileInput = new FileInputStream(localFile);
                            OutputStream dataOutput = dataSocket.getOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        // Loop through file data until end of file
                        while ((read = fileInput.read(buffer)) != -1)
                            dataOutput.write(buffer, 0, read);

                        dataOutput.flush();
                    }

                    FtpReply completionReply = readReply(controlReader);
                    // check if file transfer completed successfully (226 means closing data connection)
                    if (completionReply.code != 226)
                        throw new IOException("transfer nvr work properly: expected 226, got " + completionReply.code
                                + ": " + completionReply.message);
                }
            } else {
                try (ServerSocket activeSocket = new ServerSocket(0)) {
                    InetAddress localAddress = resolveLocalIPv4Address(controlSocket);
                    String portCommand = buildPortCommand(localAddress, activeSocket.getLocalPort());

                    sendCommand(portCommand);
                    FtpReply portReply = readReply(controlReader);
                    // check if PORT command was successful (200 means command okay)
                    if (portReply.code != 200)
                        throw new IOException(
                                "FTP PORT failed: expected 200, got " + portReply.code + ": " + portReply.message);

                    sendCommand("STOR " + remoteFileName);
                    FtpReply storReply = readReply(controlReader);
                    // check if STOR command was accepted (150 or 125 means data connection opening)
                    if (storReply.code != 150 && storReply.code != 125)
                        throw new IOException(
                                "STOR failed: expected 150 or 125, got " + storReply.code + ": " + storReply.message);

                    try (Socket dataSocket = activeSocket.accept();
                            InputStream fileInput = new FileInputStream(localFile);
                            OutputStream dataOutput = dataSocket.getOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        // Loop through file data until end of file
                        while ((read = fileInput.read(buffer)) != -1)
                            dataOutput.write(buffer, 0, read);

                        dataOutput.flush();
                    }

                    FtpReply completionReply = readReply(controlReader);
                    // check if file transfer completed successfully (226 means closing data
                    // connection)
                    if (completionReply.code != 226)
                        throw new IOException("transfer nvr work properly: expected 226, got " + completionReply.code
                                + ": " + completionReply.message);
                }
            }
        }

        private void sendCommand(String command) throws IOException {
            controlWriter.write((command + CRLF).getBytes(StandardCharsets.US_ASCII));
            controlWriter.flush();
        }

        private void sendOptionalCommand(String command) throws IOException {
            sendCommand(command);
            FtpReply reply = readReply(controlReader);
            if (reply.code >= 400 && reply.code < 600) {
                System.out.println("FTP optional command not supported: " + command + " (" + reply.code + ")");
            }
        }

        private void ensureRemoteDirectory(String remoteDirectory) throws IOException {
            if (remoteDirectory == null || remoteDirectory.isEmpty())
                return;

            sendCommand("CWD " + remoteDirectory);
            FtpReply cwdReply = readReply(controlReader);
            if (cwdReply.code == 250)
                return;

            if (cwdReply.code != 550)
                throw new IOException(
                        "CWD failed: expected 250 or 550, got " + cwdReply.code + ": " + cwdReply.message);

            if (remoteDirectory.startsWith("/")) {
                sendCommand("CWD /");
                FtpReply rootReply = readReply(controlReader);
                if (rootReply.code != 250)
                    throw new IOException(
                            "CWD / failed: expected 250, got " + rootReply.code + ": " + rootReply.message);
            }

            String[] parts = remoteDirectory.split("/");
            for (String part : parts) {
                if (part.isEmpty())
                    continue;
                sendCommand("CWD " + part);
                FtpReply partReply = readReply(controlReader);
                if (partReply.code == 250)
                    continue;

                if (partReply.code != 550)
                    throw new IOException("CWD failed for " + part + ": expected 250 or 550, and got " + partReply.code
                            + ": " + partReply.message);

                sendCommand("MKD " + part);
                FtpReply mkdReply = readReply(controlReader);
                if (mkdReply.code != 257 && mkdReply.code != 250)
                    throw new IOException("MKD failed for " + part + ": expected 257 or 250, and got " + mkdReply.code
                            + ": " + mkdReply.message);

                sendCommand("CWD " + part);
                FtpReply finalReply = readReply(controlReader);
                if (finalReply.code != 250)
                    throw new IOException("CWD failed after MKD for " + part + ": expected 250, and got " + finalReply.code
                            + ": " + finalReply.message);
            }
        }

        private PasvEndpoint enterPassiveMode() throws IOException {
            sendCommand("PASV");
            FtpReply reply = readReply(controlReader);
            if (reply.code != 227)
                throw new IOException("PASV failed: expected 227, got " + reply.code + ": " + reply.message);

            Matcher matcher = PASV_PATTERN.matcher(reply.message);
            if (!matcher.find())
                throw new IOException("Malformed PASV reply: " + reply.message);

            String host = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + "." + matcher.group(4);
            int p1 = Integer.parseInt(matcher.group(5));
            int p2 = Integer.parseInt(matcher.group(6));
            int port = p1 * 256 + p2;

            return new PasvEndpoint(host, port);
        }

        @Override
        public void close() throws IOException {
            try {
                // check if socket is still connected and open
                if (controlSocket.isConnected() && !controlSocket.isClosed()) {
                    try {
                        sendCommand("QUIT");
                        readReply(controlReader);
                    } catch (IOException ignored) {
                    }
                }
            } finally {
                controlSocket.close();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println(
                    "How to run it: java FtpBackupClient <ftpHost> <ftpPort> <username> <password> <localDirectory> <remoteBackupDirectory> [--passive] [--versioned]");
            return;
        }

        String ftpHost = args[0];
        int ftpPort = Integer.parseInt(args[1]);
        String username = args[2];
        String password = args[3];
        File localDirectory = new File(args[4]);
        String remoteBackupDirectory = args[5];
        boolean passive = false;
        boolean versioned = false;

        for (int i = 6; i < args.length; i++) {
            String flag = args[i];
            if ("--passive".equalsIgnoreCase(flag)) {
                passive = true;
            } else if ("--versioned".equalsIgnoreCase(flag)) {
                versioned = true;
            } else {
                System.err.println("Unknown option: " + flag);
                System.out.println(
                        "Usage: java FtpBackupClient <ftpHost> <ftpPort> <username> <password> <localDirectory> <remoteBackupDirectory> [--passive] [--versioned]");
                return;
            }
        }

        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            System.err.println(
                    "Local directory does not exist or is not a directory: " + localDirectory.getAbsolutePath());
            return;
        }

        // Track the last observed modification time for each .txt file.
        Map<String, Long> lastModifiedByFile = new HashMap<>();
        Map<String, Integer> versionByFile = new HashMap<>();
        Set<String> missingFiles = new HashSet<>();

        try (FtpSession session = new FtpSession(ftpHost, ftpPort, username, password, remoteBackupDirectory,
                passive)) {
            System.out.println("Connected to server: " + localDirectory.getAbsolutePath());
            System.out.println("Passive mode: " + (passive ? "on" : "off"));
            System.out.println("Versioned backups: " + (versioned ? "on" : "off"));

            // Main polling loop to continuously monitor for file changes
            while (true) {
                // Snapshot the current .txt files in the directory on each poll.
                File[] files = localDirectory.listFiles();
                Map<String, File> currentTxtFiles = new HashMap<>();

                if (files != null) {
                    for (File file : files) {
                        // check if item is a file and has .txt extension
                        if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".txt"))
                            currentTxtFiles.put(file.getName(), file);
                    }
                }

                for (Map.Entry<String, File> entry : currentTxtFiles.entrySet()) {
                    String fileName = entry.getKey();
                    File file = entry.getValue();
                    long currentLastModified = file.lastModified();
                    boolean wasMissing = missingFiles.remove(fileName);
                    Long previousLastModified = wasMissing ? null : lastModifiedByFile.get(fileName);

                    // check if file is new or has been modified since last check
                    if (previousLastModified == null || previousLastModified.longValue() != currentLastModified) {
                        int nextVersion = 0;
                        if (versioned) {
                            Integer lastVersion = versionByFile.get(fileName);
                            nextVersion = (lastVersion == null) ? 0 : (lastVersion.intValue() + 1);
                        }

                        String remoteFileName = buildRemoteFileName(fileName, nextVersion, versioned);

                        System.out.println("A change in " + fileName + ", was noticed. Uploading as " + remoteFileName + " ...");
                        try {
                            session.upload(file, remoteFileName);
                            lastModifiedByFile.put(fileName, currentLastModified);
                            if (versioned)
                                versionByFile.put(fileName, nextVersion);
                            System.out.println("Upload to remote completed: " + remoteFileName);
                        } catch (IOException uploadError) {
                            System.err.println("Upload to remote failed for " + fileName + " -> " + remoteFileName + ": "
                                    + uploadError.getMessage());
                        }
                    }
                }

                // If a file disappears locally, keep remote history and mark it missing.
                for (String knownFile : lastModifiedByFile.keySet()) {
                    if (!currentTxtFiles.containsKey(knownFile))
                        missingFiles.add(knownFile);
                }

                if (!missingFiles.isEmpty()) {
                    System.out.println("Local deletions detected. Remote history preserved for: " + missingFiles);
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    System.out.println("Stopped checking.");
                    return;
                }
            }
        } catch (IOException exception) {
            System.err.println("FTP client error: " + exception.getMessage());
            exception.printStackTrace(System.err);
        }
    }

    private static FtpReply readReply(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null)
            throw new IOException("FTP server closed unexpectedly.");

        StringBuilder message = new StringBuilder(firstLine);
        // check if this is a multi-line FTP reply (format: xxx- where x is digit)
        if (firstLine.length() >= 4 && Character.isDigit(firstLine.charAt(0)) && Character.isDigit(firstLine.charAt(1))
                &&
                Character.isDigit(firstLine.charAt(2)) && firstLine.charAt(3) == '-') {

            String replyCode = firstLine.substring(0, 3);
            // Loop through remaining lines of multiline reply until end marker is found
            while (true) {
                String line = reader.readLine();
                // check if reply was truncated
                if (line == null)
                    throw new IOException("FTP multiline reply was truncated.");
                message.append("\n").append(line);
                // check if this is the last line of multiline reply (format: xxx )
                if (line.length() >= 4 && line.startsWith(replyCode) && line.charAt(3) == ' ')
                    break;
            }
        }

        if (firstLine.length() < 3)
            throw new IOException("Malformed FTP reply: " + firstLine);

        int code;
        try {
            code = Integer.parseInt(firstLine.substring(0, 3));
        } catch (NumberFormatException exception) {
            throw new IOException("Corrupted FTP reply code: " + firstLine, exception);
        }

        return new FtpReply(code, message.toString());
    }

    private static String buildPortCommand(InetAddress address, int port) {
        String[] octets = address.getHostAddress().split("\\.");
        if (octets.length != 4)
            throw new IllegalArgumentException("We requires an IPv4 address, got: " + address.getHostAddress());

        int p1 = port / 256;
        int p2 = port % 256;
        return "PORT " + octets[0] + "," + octets[1] + "," + octets[2] + "," + octets[3] + "," + p1 + "," + p2;
    }

    private static InetAddress resolveLocalIPv4Address(Socket controlSocket) throws IOException {
        InetAddress socketAddress = controlSocket.getLocalAddress();
        // check if socket's local address is a valid IPv4 address (not any address)
        if (socketAddress instanceof Inet4Address && !socketAddress.isAnyLocalAddress())
            return socketAddress;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            // Loop through all available network interfaces
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback())
                    continue;

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                // Loop through all addresses on this network interface
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress())
                        return address;
                }
            }
        } catch (Exception exception) {
            throw new IOException("Cannot get a local IPv4 address for FTP PORT.", exception);
        }

        InetAddress localhost = InetAddress.getLocalHost();
        if (localhost instanceof Inet4Address)
            return localhost;

        throw new IOException("Cannot get a usable IPv4 address for FTP PORT.");
    }

    private static String buildRemoteFileName(String fileName, int version, boolean versioned) {
        if (!versioned)
            return fileName;
        String suffix = String.format(Locale.ROOT, "%03d", version);
        return fileName + "." + suffix;
    }

}