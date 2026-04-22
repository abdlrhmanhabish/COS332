//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.UUID;

public class VacationResponder {

    private static final int PORT = 110;
    private static class EmailHeaders {
        private final String from;
        private final String subject;

        EmailHeaders(String from, String subject) {
            this.from = from;
            this.subject = subject;
        }
    }

    // so basically , just egts email address from header 
    private static String extract(String fromHeader) {
        if (fromHeader == null) return null;

        String trimmed = fromHeader.trim();
        int start = trimmed.indexOf('<');
        int end = trimmed.indexOf('>');

        // If the header has <...>, use the address inside angle brackets 
        if (start >= 0 && end > start) return trimmed.substring(start + 1, end).trim();

        int atIndex = trimmed.indexOf('@');
        // A valid email local-part must exist before '@'; otherwise treat header as invalid.
        if (atIndex <= 0) return null;

        int left = atIndex - 1;
        while (left >= 0 && !Character.isWhitespace(trimmed.charAt(left)) && trimmed.charAt(left) != '<' && trimmed.charAt(left) != '"') {
            left--;
        }

        int right = atIndex + 1;
        while (right < trimmed.length() && !Character.isWhitespace(trimmed.charAt(right)) && trimmed.charAt(right) != '>' && trimmed.charAt(right) != '"') {
            right++;
        }

        String possibleEmail = trimmed.substring(left + 1, right).replaceAll("[;,]$", "").trim();
        if (possibleEmail.contains("@")) return possibleEmail;

        return null;
    }

    // Validating POP3 responses, throws IOException if response is null or doesn't start with +OK
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

        try { return Integer.parseInt(parts[1]); } 
        catch (NumberFormatException e) { throw new IOException("Invalid message count in STAT response: " + statResponse, e); }
    }

    // Runs TOP <n> 0, reads headers until '.', and returns sender + subject only.
    private static EmailHeaders readTopHeaders(BufferedReader in, BufferedWriter out, int messageNumber) throws IOException {
        sendCommand(out, "TOP " + messageNumber + " 0");
        String topResponse = in.readLine();
        expectPopOk(topResponse, "TOP " + messageNumber + " 0");

        String fromHeader = null;
        String subjectHeader = "";

        String line;
        while ((line = in.readLine()) != null) {
            // POP3 marks end of TOP output with a single dot line.
            if (".".equals(line)) break;

            // Capture sender from the From header for reply else capture subject for reply, ignore other headers
            if (line.toLowerCase().startsWith("from:")) fromHeader = line.substring(5).trim();
            else if (line.toLowerCase().startsWith("subject:")) subjectHeader = line.substring(8).trim();
        }

        String sender = extract(fromHeader);
        return new EmailHeaders(sender, subjectHeader);
    }

    // Prac 6 code, never change anything
    private static String readServerReply(BufferedReader in) throws IOException {
        String firstLine = in.readLine();
        if (firstLine == null) {
            throw new IOException("SMTP server closed connection unexpectedly.");
        }

        StringBuilder response = new StringBuilder(firstLine);
        while (firstLine.length() >= 4 && firstLine.charAt(3) == '-') {
            String continuation = in.readLine();
            if (continuation == null) {
                break;
            }
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

            // bonus marks RFC 5321 Section 4.1.1.1, modern SMTP server wants EHLO and not HELO, readServersReply handles the multiple line list thing
            sendCommand(out, "EHLO localhost");
            response = readServerReply(in);
            if (!response.startsWith("250")) {
                sendCommand(out, "HELO localhost");
                response = readServerReply(in);
                checkReplyCode(response, 250);
            }

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
            // bonus marks: RFC 5322 Section 3.6, requires date header in  rfc 1123 format
            out.write("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC"))));
            out.write("\r\n");
            // bonus marks: unique message ID, format is not strictly defined but this is a common approach
            out.write("MessageID: <" + UUID.randomUUID().toString() + "@eventreminder.local>\r\n");
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
        if (args.length != 6) {
            System.out.println("Usage: java VacationResponder <pop3Host> <email> <password> <smtpHost> <smtpPort> <fromEmail>");
            return;
        }

        String pop3Host = args[0];
        String email = args[1];
        String password = args[2];
        String smtpHost = args[3];
        int smtpPort;

        try {
            smtpPort = Integer.parseInt(args[4]);
        } 
        catch (NumberFormatException e) {
            System.err.println("Invalid SMTP port: " + args[4]);
            return;
        }

        String fromEmail = args[5];
        HashSet<String> repliedSenders = new HashSet<>();

        try (
                Socket popSocket = new Socket(pop3Host, PORT);
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
                if (headers.from == null || headers.from.isEmpty()) continue;
                if (repliedSenders.contains(headers.from)) {
                    System.out.println("Skipping " + headers.from + " (already replied during this session).");
                    continue;
                }

                repliedSenders.add(headers.from);
                String replySubject = "Re: " + headers.subject;
                String replyBody = "I am currently on vacation and will reply when I return.";
                System.out.println("Sending auto-reply to: " + headers.from + " | Subject: " + replySubject);
                sendEmail(smtpHost, smtpPort, fromEmail, headers.from, replySubject, replyBody);
            }

            System.out.println("Finished checking emails. Logging out.");
            sendCommand(popOut, "QUIT");
            response = popIn.readLine();
            expectPopOk(response, "QUIT");

        } 
        catch (IOException e) {
            System.err.println("VacationResponder failed: " + e.getMessage());
        }
    }
}
