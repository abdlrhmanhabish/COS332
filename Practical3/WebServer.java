//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class WebServer {

    private static final String CRLF = "\r\n";

    // Class to hold parsed HTTP request info
    private static class Request {
        final String method;  // HTTP methods
        final String target;  // Request path

        Request(String method, String target) {
            this.method = method;
            this.target = target;
        }
    }

    public static void main(String[] args) {
        // choose port number
        int port = 8888;
        // open server socket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            // Accept requests , each on a new thread
           while (true) {
                Socket s = serverSocket.accept(); 
                // Hand the socket off to a new thread immediately
                new Thread(() -> {
                    try {
                        handleClient(s);
                    } catch (IOException exception) {
                        System.err.println("Client handling error: " + exception.getMessage());
                    } finally {
                        // Ensure the socket is always closed when the thread finishes
                        try {
                            s.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } 
        catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    // Process a single HTTP request from a client socket and send the appropriate response
    private static void handleClient(Socket s) throws IOException {
        s.setSoTimeout(5000);

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)
        );
        OutputStream output = s.getOutputStream();

        Request request = readRequest(reader);
        // If request parsing failed, return 400 
        if (request == null) {
            writeResponse(output, 400, "Bad Request", "text/plain; charset=utf-8", "Bad Request", false);
            return;
        }

        String method = request.method;
        String path = normalizePath(request.target);

        // Only allow GET and HEAD methods
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            writeMethodNotAllowed(output, "HEAD".equals(method));
            return;
        }

        // Return 404 for favicon requests
        if ("/favicon.ico".equals(path)) {
            writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found", "HEAD".equals(method));
            return;
        }

        // I used this to test if it was working, we need to combine it with the frontend so our thing works properly
        String body = "Backend running. method=" + method + " path=" + path;
        writeResponse(output, 200, "OK", "text/plain; charset=utf-8", body, "HEAD".equals(method));
    }

    // Parse the HTTP request line and consume all headers from the input stream
    private static Request readRequest(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null || firstLine.isEmpty()) return null;

        String[] parts = firstLine.split("\\s+");
        if (parts.length < 3) {
            consumeHeaders(reader);
            return null;
        }

        consumeHeaders(reader);
        return new Request(parts[0], parts[1]);
    }

    // Read and discard all HTTP headers until the blank line is reached
    private static void consumeHeaders(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) break;
        }
    }

    // Extract the path from the request target
    private static String normalizePath(String rawTarget) {
        String target = rawTarget;
        int queryIndex = target.indexOf('?');

        if (queryIndex >= 0) target = target.substring(0, queryIndex);
        if (target.isEmpty()) return "/";

        return target;
    }

    // Send a 405 Method Not Allowed response with the Allow header listing allowed methods
    private static void writeMethodNotAllowed(OutputStream output, boolean headOnly) throws IOException {
        byte[] bodyBytes = "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 405 Method Not Allowed").append(CRLF)
            .append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append(CRLF)
            .append("Content-Type: text/plain; charset=utf-8").append(CRLF)
            .append("Content-Length: ").append(bodyBytes.length).append(CRLF)
            .append("Allow: GET, HEAD").append(CRLF)
            .append("Cache-Control: no-cache, no-store").append(CRLF)
            .append("Pragma: no-cache").append(CRLF)
            .append("Connection: close").append(CRLF)
            .append(CRLF);

        output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        if (!headOnly) output.write(bodyBytes);
        output.flush();
    }

    // Write a HTTP response with status line, headers and body
    private static void writeResponse(
        OutputStream output,
        int statusCode,
        String reasonPhrase,
        String contentType,
        String body,
        boolean headOnly
    ) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(statusCode).append(' ').append(reasonPhrase).append(CRLF)
            .append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append(CRLF)
            .append("Content-Type: ").append(contentType).append(CRLF)
            .append("Content-Length: ").append(bodyBytes.length).append(CRLF)
            .append("Cache-Control: no-cache, no-store").append(CRLF)
            .append("Pragma: no-cache").append(CRLF)
            .append("Connection: close").append(CRLF)
            .append(CRLF);

        output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        if (!headOnly) {
            output.write(bodyBytes);
        }
        output.flush();
    }
}