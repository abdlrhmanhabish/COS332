//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebServer {

    private static final String CRLF = "\r\n";

    private static final Map<String, String> CITIES;
    static {
        CITIES = new LinkedHashMap<>();
        CITIES.put("London", "Europe/London");
        CITIES.put("New York", "America/New_York");
        CITIES.put("Tokyo", "Asia/Tokyo");
        CITIES.put("Sydney", "Australia/Sydney");
        CITIES.put("Riyadh", "Asia/Riyadh");
        CITIES.put("Berlin", "Europe/Berlin");
        CITIES.put("Beijing", "Asia/Shanghai");
        CITIES.put("Sao Paulo", "America/Sao_Paulo");
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss, EEEE dd MMMM yyyy");

    // class to hold parsed HTTP request info
    private static class Request {
        final String method;
        final String target;

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
                    } catch (java.net.SocketTimeoutException ignored) {
                        // browser opened a connection but sent no request — normal, ignore silently
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
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    // Process a single HTTP request from a client socket and send the appropriate
    // response
    private static void handleClient(Socket s) throws IOException {
        s.setSoTimeout(30000); // 30 s — enough time to type a request in telnet

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
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

        String cityName = null;
        if (!"/".equals(path)) {
            try {
                cityName = java.net.URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8.name());
            } catch (IllegalArgumentException e) {
                cityName = path.substring(1);
            }
            if (!CITIES.containsKey(cityName)) {
                writeResponse(output, 404, "Not Found", "text/html; charset=utf-8",
                        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>404</title></head>"
                                + "<body><h1>404 Not Found</h1><p>Unknown city.</p></body></html>",
                        "HEAD".equals(method));
                return;
            }
        }

        String body = buildHtml(cityName);
        writeResponse(output, 200, "OK", "text/html; charset=utf-8", body, "HEAD".equals(method));
    }

    // Parse the HTTP request line and consume all headers from the input stream
    private static Request readRequest(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null || firstLine.isEmpty())
            return null;

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
            if (line.isEmpty())
                break;
        }
    }

    // Extract the path from the request target
    private static String normalizePath(String rawTarget) {
        String target = rawTarget;
        int queryIndex = target.indexOf('?');

        if (queryIndex >= 0)
            target = target.substring(0, queryIndex);
        if (target.isEmpty())
            return "/";

        return target;
    }

    // Send a 405 Method Not Allowed response with the Allow header listing allowed
    // methods
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
        if (!headOnly)
            output.write(bodyBytes);
        output.flush();
    }

    // Write a HTTP response with status line, headers and body
    private static void writeResponse(
            OutputStream output,
            int statusCode,
            String reasonPhrase,
            String contentType,
            String body,
            boolean headOnly) throws IOException {
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

    private static String formatTime(String zoneId) {
        return ZonedDateTime.now(ZoneId.of(zoneId)).format(TIME_FMT);
    }

    private static String buildHtml(String selectedCity) {
        String saTime = formatTime("Africa/Johannesburg");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <meta http-equiv=\"refresh\" content=\"1\">\n")
                .append("  <title>World Clock</title>\n")
                .append("  <style>\n")
                .append("    body{font-family:Arial,sans-serif;max-width:790px;margin:40px auto;background:#f0f2f5;}\n")
                .append("    h1{color:#222;}\n")
                .append("    .box{background:#fff;border-radius:8px;padding:20px 24px;margin-bottom:16px;")
                .append("box-shadow:0 2px 6px rgba(0,0,0,.12);}\n")
                .append("    h2{margin:0 0 6px;color:#444;font-size:1.1em;}\n")
                .append("    .time{font-size:1.5em;font-weight:bold;color:#0057b8;}\n")
                .append("    ul{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:10px;margin-top:8px;}\n")
                .append("    li a{padding:8px 16px;background:#0057b8;color:#fff;border-radius:5px;")
                .append("text-decoration:none;font-size:.95em;}\n")
                .append("    li a:hover{background:#003d82;}\n")
                .append("  </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <h1>World Clock</h1>\n")
                .append("  <div class=\"box\">\n")
                .append("    <h2>South Africa &#8212; Johannesburg</h2>\n")
                .append("    <p class=\"time\">").append(saTime).append("</p>\n")
                .append("  </div>\n");

        if (selectedCity != null) {
            String cityTime = formatTime(CITIES.get(selectedCity));
            sb.append("  <div class=\"box\">\n")
                    .append("    <h2>").append(escapeHtml(selectedCity)).append("</h2>\n")
                    .append("    <p class=\"time\">").append(cityTime).append("</p>\n")
                    .append("  </div>\n");
        }

        sb.append("  <div class=\"box\">\n")
                .append("    <h2>Select a city</h2>\n")
                .append("    <div style=\"margin-top:14px;\"></div>\n")
                .append("    <ul>\n");

        for (String city : CITIES.keySet()) {
            String encoded;
            try {
                encoded = java.net.URLEncoder.encode(city, StandardCharsets.UTF_8.name())
                        .replace("+", "%20");
            } catch (java.io.UnsupportedEncodingException e) {
                encoded = city;
            }
            sb.append("      <li><a href=\"/").append(encoded).append("\">")
                    .append(escapeHtml(city)).append("</a></li>\n");
        }

        sb.append("    </ul>\n")
                .append("  </div>\n")
                .append("</body>\n")
                .append("</html>\n");

        return sb.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}