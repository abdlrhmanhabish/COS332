//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

public class VacationResponder {

    private static final int DEFAULT_POP3_PORT = 1110;
    private static final String SUBJECT = "prac7";
    private static final int POLL_INTERVAL = 10;
    private static final String REPLIED_SENDERS = "replied_senders.txt";
    private static final String CONFIG_FILE = "vacation.properties";

    // Default values 
    private static final String DEFAULT_POP3_HOST = "localhost";
    private static final int DEFAULT_POP3_PORT_FALLBACK = 1110;
    private static final String DEFAULT_EMAIL = "secondemail@test.local";
    private static final String DEFAULT_PASSWORD = "password123";
    private static final String DEFAULT_SMTP_HOST = "localhost";
    private static final int DEFAULT_SMTP_PORT = 1025;
    private static final String DEFAULT_FROM_EMAIL = "reminders@local.test";
    private static final String DEFAULT_REPLY_TO_EMAILS = "tester1@test.com,tester2@test.com";

    private static int envIntOrDefault(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty())return fallback;

        try {
            return Integer.parseInt(value.trim());
        } 
        catch (NumberFormatException e) { return fallback;}
    }

    private static String envStringOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty())return fallback;
        return value.trim();
    }

    private static String propertyOrDefault(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty())return fallback;
        return value.trim();
    }

    private static int propertyIntOrDefault(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) return fallback;

        try { return Integer.parseInt(value.trim());} 
        catch (NumberFormatException e) {return fallback;}
    }

    private static List<String> parseEmailList(String raw) {
        List<String> emails = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty())return emails;

        String[] parts = raw.split(",");
        for (String part : parts) {
            String email = part.trim();
            if (!email.isEmpty())emails.add(email);
        }
        return emails;
    }

    private static Properties loadConfigProperties(Path configPath) throws IOException {
        Properties properties = new Properties();
        if (!Files.exists(configPath)) return properties;
        try (FileInputStream in = new FileInputStream(configPath.toFile())) {properties.load(in);}

        return properties;
    }

    private static class EmailHeaders {
        private final String from;
        private final String subject;
        private final boolean isMailingList;

        EmailHeaders(String from, String subject, boolean isMailingList) {
            this.from = from;
            this.subject = subject;
            this.isMailingList = isMailingList;
        }
    }

    // so basically , just Gets email address from header
    private static String extract(String fromHeader) {
        if (fromHeader == null)
            return null;

        String trimmed = fromHeader.trim();
        int start = trimmed.indexOf('<');
        int end = trimmed.indexOf('>');

        // If the header has <...>, use the address inside angle brackets
        if (start >= 0 && end > start) return trimmed.substring(start + 1, end).trim();

        int atIndex = trimmed.indexOf('@');
        // A valid email local part must exist before '@'; otherwise treat header as invalid.
        if (atIndex <= 0)return null;

        int left = atIndex - 1;
        while (left >= 0 && !Character.isWhitespace(trimmed.charAt(left)) && trimmed.charAt(left) != '<'&& trimmed.charAt(left) != '"') {
            left--;
        }

        int right = atIndex + 1;
        while (right < trimmed.length() && !Character.isWhitespace(trimmed.charAt(right))&& trimmed.charAt(right) != '>' && trimmed.charAt(right) != '"') {
            right++;
        }

        String possibleEmail = trimmed.substring(left + 1, right).replaceAll("[;,]$", "").trim();
        if (possibleEmail.contains("@"))return possibleEmail;

        return null;
    }

    // Validating POP3 responses
    private static void expectPopOk(String response, String command) throws IOException {
        if (response == null || !response.startsWith("+OK")) {
            throw new IOException("POP3 command failed (" + command + "): " + response);
        }
    }

    // Parses STAT response and returns the number of messages in the inbox.
    private static int parseMessageCount(String statResponse) throws IOException {
        String[] parts = statResponse.split("\\s+");
        // STAT must look like: "+OK <count> <size>"; without a second token, count cannot be read.
        if (parts.length < 2) throw new IOException("Invalid STAT response: " + statResponse);

        try {
            return Integer.parseInt(parts[1]);
        } 
        catch (NumberFormatException e) {throw new IOException("Invalid message count in STAT response: " + statResponse, e);}
    }

    // Runs TOP <n> 0, reads headers until '.', and returns sender + subject +mailing list flag.
    private static EmailHeaders readTopHeaders(BufferedReader in, BufferedWriter out, int messageNumber)
            throws IOException {
        sendCommand(out, "TOP " + messageNumber + " 0");
        String topResponse = in.readLine();
        expectPopOk(topResponse, "TOP " + messageNumber + " 0");

        String fromHeader = null;
        String subjectHeader = "";
        boolean isMailingList = false;

        String line;
        while ((line = in.readLine()) != null) {
            // POP3 marks end of TOP output with a single dot line.
            if (".".equals(line))break;

            String lowerLine = line.toLowerCase();
            // Capture sender from the From header for reply else capture subject for reply,ignore other headers
            if (lowerLine.startsWith("from:"))fromHeader = line.substring(5).trim();
            else if (lowerLine.startsWith("subject:")) subjectHeader = line.substring(8).trim();
            else if (lowerLine.startsWith("list-id:") || lowerLine.startsWith("list-post:")) isMailingList = true;
        }

        String sender = extract(fromHeader);
        return new EmailHeaders(sender, subjectHeader, isMailingList);
    }

    private static HashSet<String> loadRepliedSenders(Path repliedSendersPath) throws IOException {
        HashSet<String> replied = new HashSet<>();
        if (!Files.exists(repliedSendersPath)) {
            Files.createFile(repliedSendersPath);
            return replied;
        }

        List<String> lines = Files.readAllLines(repliedSendersPath);
        for (String line : lines) {
            String sender = line.trim().toLowerCase();
            if (!sender.isEmpty())replied.add(sender);
        }
        return replied;
    }

    private static void persistRepliedSender(Path repliedSendersPath, String sender) throws IOException {
        List<String> line = new ArrayList<>();
        line.add(sender.toLowerCase());
        Files.write(
                repliedSendersPath,line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void checkMailboxOnce(
            String pop3Host, int pop3Port, String email,
            String password, String smtpHost, int smtpPort,
            String fromEmail, List<String> replyToEmails, int[] replyToIndex,
            HashSet<String> repliedSenders, Path repliedSendersPath) throws IOException {
        try (
            Socket popSocket = new Socket(pop3Host, pop3Port);
                BufferedReader popIn = new BufferedReader(new InputStreamReader(popSocket.getInputStream()));
                BufferedWriter popOut = new BufferedWriter(new OutputStreamWriter(popSocket.getOutputStream()))) {

            String response = popIn.readLine();
            expectPopOk(response, "greeting");

            sendCommand(popOut, "USER " + email);
            response = popIn.readLine();
            expectPopOk(response, "USER");

            sendCommand(popOut, "PASS " + password);
            response = popIn.readLine();
            expectPopOk(response, "PASS");

            sendCommand(popOut, "STAT");
            response = popIn.readLine();
            expectPopOk(response, "STAT");
            int totalEmails = parseMessageCount(response);

            System.out.println("Successfully logged in. Found " + totalEmails + " email(s) in inbox.");

            for (int i = 1; i <= totalEmails; i++) {
                EmailHeaders headers = readTopHeaders(popIn, popOut, i);
                if (headers.from == null || headers.from.isEmpty())continue;

                // Skip mailing list emails
                if (headers.isMailingList) {
                    System.out.println("Skipping " + headers.from + " (mailing list detected).");
                    continue;
                }

                String subject = headers.subject == null ? "" : headers.subject.trim();
                if (!SUBJECT.equalsIgnoreCase(subject)) {
                    System.out.println("Skipping " + headers.from + " (subject is not '" + SUBJECT + "').");
                    continue;
                }

                String replySubject = "Re: " + headers.subject;
                String replyBody = "I am currently on vacation and will reply when I return.";
                String replyTo = replyToEmails.get(replyToIndex[0] % replyToEmails.size());
                replyToIndex[0]++;

                String normalizedReplyTarget = replyTo.trim().toLowerCase();
                if (repliedSenders.contains(normalizedReplyTarget)) {
                    System.out.println("we skiiping coz we already replied to: " + replyTo);
                    continue;
                }

                System.out.println("Relying to: " + replyTo +  " - Subject: " + replySubject);
                sendEmail(smtpHost, smtpPort, fromEmail, replyTo, replySubject, replyBody);
                repliedSenders.add(normalizedReplyTarget);
                persistRepliedSender(repliedSendersPath, normalizedReplyTarget);
            }
            System.out.println();
            System.out.println("We are done with all emails.");
            sendCommand(popOut, "QUIT");
            response = popIn.readLine();
            expectPopOk(response, "QUIT");
        }
    }

    // Prac 6 code, never change anything
    private static String readServerReply(BufferedReader in) throws IOException {
        String firstLine = in.readLine();
        if (firstLine == null) throw new IOException("SMTP server closed connection due to unknown error");

        StringBuilder response = new StringBuilder(firstLine);
        while (firstLine.length() >= 4 && firstLine.charAt(3) == '-') {
            String continuation = in.readLine();
            if (continuation == null) break;
            response.append("\n").append(continuation);
            firstLine = continuation;
        }

        return response.toString();
    }

    // Prac 6 code, never change anything
    private static void sendCommand(BufferedWriter out, String command) throws IOException {
        out.write(command + "\r\n");
        out.flush();
    }

    // Prac 6 code, never change anything
    private static void checkReplyCode(String response, int expectedPrefix) throws IOException {
        if (!response.startsWith(String.valueOf(expectedPrefix))) {
            throw new IOException("SMTP error. Expected " + expectedPrefix + "xx but got: " + response);
        }
    }

    // Prac 6 code, never change anything
    public static void sendEmail(
            String smtpHost, int smtpPort, String from, String to, String subject, String body) throws IOException {
        try (
                Socket socket = new Socket(smtpHost, smtpPort);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            String response = readServerReply(in);
            checkReplyCode(response, 220);

            sendCommand(out, "HELO localhost");
            response = readServerReply(in);
            checkReplyCode(response, 250);

            sendCommand(out, "MAIL FROM:<" + from + ">");
            response = readServerReply(in);
            checkReplyCode(response, 250);

            sendCommand(out, "RCPT TO:<" + to + ">");
            response = readServerReply(in);
            checkReplyCode(response, 250);

            sendCommand(out, "DATA");
            response = readServerReply(in);
            checkReplyCode(response, 354);

            out.write("From: " + from + "\r\n");
            out.write("To: " + to + "\r\n");
            out.write("Subject: " + subject + "\r\n");
            out.write("\r\n");
            out.write(body + "\r\n");
            out.write(".\r\n");
            out.flush();

            response = readServerReply(in);
            checkReplyCode(response, 250);

            sendCommand(out, "QUIT");
            response = readServerReply(in);
            checkReplyCode(response, 221);
        }
    }

    public static void main(String[] args) {
        if (args.length != 0 && args.length != 6 && args.length != 7) {
            System.out.println("Usage: java VacationResponder <pop3Host> <email> <password> <smtpHost> <smtpPort> <fromEmail> [pop3Port]");
            System.out.println("Or run without args to use built-in defaults for local testing.");
            return;
        }

        String pop3Host;
        int pop3Port;
        String email;
        String password;
        String smtpHost;
        int smtpPort;
        String fromEmail;
        List<String> replyToEmails;

        if (args.length == 0) {
            Properties properties;
            try {
            properties = loadConfigProperties(Paths.get(CONFIG_FILE));
            } catch (IOException e) {
            System.err.println("Could not read " + CONFIG_FILE + ": " + e.getMessage());
            return;
            }

            pop3Host = propertyOrDefault(properties, "pop3.host", envStringOrDefault("VACATION_POP3_HOST", DEFAULT_POP3_HOST));
            pop3Port = propertyIntOrDefault(properties, "pop3.port", envIntOrDefault("VACATION_POP3_PORT", DEFAULT_POP3_PORT_FALLBACK));
            email = propertyOrDefault(properties, "email", envStringOrDefault("VACATION_EMAIL", DEFAULT_EMAIL));
            password = propertyOrDefault(properties, "password", envStringOrDefault("VACATION_PASSWORD", DEFAULT_PASSWORD));
            smtpHost = propertyOrDefault(properties, "smtp.host", envStringOrDefault("VACATION_SMTP_HOST", DEFAULT_SMTP_HOST));
            smtpPort = propertyIntOrDefault(properties, "smtp.port", envIntOrDefault("VACATION_SMTP_PORT", DEFAULT_SMTP_PORT));
            fromEmail = propertyOrDefault(properties, "from.email", envStringOrDefault("VACATION_FROM_EMAIL", DEFAULT_FROM_EMAIL));
            String replyToEmailsRaw = propertyOrDefault(properties, "reply.to.emails", envStringOrDefault("VACATION_REPLY_TO_EMAILS", DEFAULT_REPLY_TO_EMAILS));
            replyToEmails = parseEmailList(replyToEmailsRaw);
            if (replyToEmails.isEmpty()) {
                System.err.println("No valid reply.to.emails configured.");
                return;
            }

            // System.out.println("POP3 host: " + pop3Host);
            // System.out.println("POP3 port: " + pop3Port);
            // System.out.println("Email: " + email);
            // System.out.println("SMTP host: " + smtpHost);
            // System.out.println("SMTP port: " + smtpPort);
            // System.out.println("From email: " + fromEmail);
            // System.out.println("Reply-to targets: " + String.join(", ", replyToEmails));
        } 
        else {
            pop3Host = args[0];
            email = args[1];
            password = args[2];
            smtpHost = args[3];

            try {
                smtpPort = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid SMTP port: " + args[4]);
                return;
            }

            fromEmail = args[5];
            replyToEmails = parseEmailList(DEFAULT_REPLY_TO_EMAILS);

            if (args.length == 7) {
                try {
                    pop3Port = Integer.parseInt(args[6]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid POP3 port: " + args[6]);
                    return;
                }
            } else {
                pop3Port = DEFAULT_POP3_PORT;
            }
        }

        Path repliedSendersPath = Paths.get(REPLIED_SENDERS);

        try {
            HashSet<String> repliedSenders = loadRepliedSenders(repliedSendersPath);
            int[] replyToIndex = new int[] { 0 };
            System.out.println("Loaded " + repliedSenders.size() + " previously replied senders");
            System.out.println("Vacation responder is working. Polling every " + POLL_INTERVAL + " seconds");

            while (true) {
                try {
                    checkMailboxOnce(pop3Host, pop3Port, email, password, smtpHost, smtpPort, fromEmail,
                            replyToEmails, replyToIndex,
                            repliedSenders,
                            repliedSendersPath);
                }
                catch (ConnectException e) {
                    System.err.println("issue connecting to porty " + pop3Host + ":" + pop3Port + ", ensure server is running");
                }
                catch (IOException e) {
                    System.err.println("failed: " + e.getMessage());
                }

                try {
                    Thread.sleep(POLL_INTERVAL * 1000L);
                } 
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } 
        catch (IOException e) {
            System.err.println("VacationResponder failed: " + e.getMessage());
        }
    }
}
