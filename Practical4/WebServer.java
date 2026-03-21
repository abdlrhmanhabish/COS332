//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {

    private static final String CRLF = "\r\n";

    private static final String PRIMARY_DATA_FILE = "Practical2/appointments.txt";
    private static final String FALLBACK_DATA_FILE = "appointments.txt";

    private static final ArrayList<Appointment> appointments = new ArrayList<>();
    private static final Object appointmentsLock = new Object();
    private static String appointmentsFilePath;

    static {
        appointmentsFilePath = resolveDataFilePath();
        loadAppointments();
    }

    private static class Appointment {
        private int id;
        private Date date;
        private Time time;
        private String withWhom;

        public Appointment(int id, String date, String time, String withWhom) {
            this.id = id;
            this.date = Date.valueOf(date);
            this.time = Time.valueOf(time);
            this.withWhom = withWhom;
        }
        //getters
        public String getTxtFormat() {
            String output = String.valueOf(id) + "#" + date + "@" + time + ">" + withWhom + "<";
            return output;
        }

        public int getId() {
            return id;
        }

        public Date getDate() {
            return date;
        }

        public Time getTime() {
            return time;
        }

        public String getWithWhom() {
            return withWhom;
        }
        //setters
        public void setDate(String date) {
            this.date = Date.valueOf(date);
        }

        public void setTime(String time) {
            this.time = Time.valueOf(time);
        }

        public void setWithWhom(String withWhom) {
            this.withWhom = withWhom;
        }
    }

    // class to hold parsed HTTP request info
    private static class Request {
        final String method;
        final String target;
        final String body;

        Request(String method, String target, String body) {
            this.method = method;
            this.target = target;
            this.body = body;
        }
    }

    public static void main(String[] args) {
        // choose port number
        int port = 8888;
        // open server socket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
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

    // Process a single HTTP request from a client socket and send the appropriate response
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
        boolean headOnly = "HEAD".equals(method);

        // Return 404 for favicon requests
        if ("/favicon.ico".equals(path)) {
            writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found", headOnly);
            return;
        }

        if ("GET".equals(method) || "HEAD".equals(method)) {
            if (!"/".equals(path)) {
                writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found", headOnly);
                return;
            }
            // root route returns HTML main page.
            String body = buildMainPageHtml("", null);
            writeResponse(output, 200, "OK", "text/html; charset=utf-8", body, headOnly);
            return;
        }

        if ("POST".equals(method)) {
            String feedback;
            Appointment searchResult = null;
            // read POST body variables into a map.
            Map<String, String> form = parseForm(request.body);

            try {
                if ("/add".equals(path)) {
                    feedback = addAppointment(form.get("date"), form.get("time"), form.get("withWhom"));
                } else if ("/delete".equals(path)) {
                    feedback = deleteAppointment(form.get("id"));
                } else if ("/search".equals(path)) {
                    SearchResponse response = searchAppointment(form.get("id"));
                    feedback = response.message;
                    searchResult = response.appointment;
                } else if ("/edit".equals(path)) {
                    feedback = editAppointment(form.get("id"), form.get("date"), form.get("time"), form.get("withWhom"));
                } else {
                    writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found", false);
                    return;
                }
            } catch (IllegalArgumentException e) {
                feedback = "Invalid date or time format. Use YYYY-MM-DD and HH:MM:SS.";
            }

            // after actions, render updated HTML main page.
            String body = buildMainPageHtml(feedback, searchResult);
            writeResponse(output, 200, "OK", "text/html; charset=utf-8", body, false);
            return;
        }

        writeMethodNotAllowed(output, headOnly);
    }

    // Parse the HTTP request line and consume all headers from the input stream
    private static Request readRequest(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null || firstLine.isEmpty())
            return null;

        String[] parts = firstLine.split("\\s+");
        if (parts.length < 3) {
            return null;
        }

        String line;
        Map<String, String> headers = new HashMap<>();
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            int sep = line.indexOf(':');
            if (sep > 0) {
                String key = line.substring(0, sep).trim().toLowerCase();
                String value = line.substring(sep + 1).trim();
                headers.put(key, value);
            }
        }

        int contentLength = 0;
        if (headers.containsKey("content-length")) {
            try {
                contentLength = Integer.parseInt(headers.get("content-length"));
            } catch (NumberFormatException ignored) {
                contentLength = 0;
            }
        }

        String body = "";
        if (contentLength > 0) {
            char[] payload = new char[contentLength];
            int readTotal = 0;
            while (readTotal < contentLength) {
                int read = reader.read(payload, readTotal, contentLength - readTotal);
                if (read == -1)
                    break;
                readTotal += read;
            }
            body = new String(payload, 0, readTotal);
        }

        return new Request(parts[0], parts[1], body);
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
                .append("Date: ")
                .append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC")))).append(CRLF)
                .append("Content-Type: text/plain; charset=utf-8").append(CRLF)
                .append("Content-Length: ").append(bodyBytes.length).append(CRLF)
                .append("Allow: GET, HEAD, POST").append(CRLF)
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
                .append("Date: ")
                .append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC")))).append(CRLF)
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

    private static String buildMainPageHtml(String feedbackMessage, Appointment searchResult) {
        List<Appointment> snapshot = getAppointmentsSnapshot();
        String serverTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(ZonedDateTime.now(ZoneId.systemDefault()));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("  <title>Appointment Manager</title>\n")
                .append("  <style>\n")
                .append("    body{font-family:Verdana,sans-serif;max-width:1100px;margin:26px auto;background:#eef2f7;color:#1f2937;padding:0 12px;}\n")
                .append("    h1{margin:0 0 8px;}\n")
                .append("    .meta{color:#4b5563;margin-bottom:14px;}\n")
                .append("    .panel{background:#fff;border:1px solid #d7dde6;border-radius:10px;padding:16px;margin-bottom:14px;box-shadow:0 1px 4px rgba(0,0,0,.08);}\n")
                .append("    .status{padding:10px;border-radius:8px;background:#ecfeff;border:1px solid #99f6e4;margin-bottom:14px;}\n")
                .append("    table{width:100%;border-collapse:collapse;background:#fff;}\n")
                .append("    th,td{border:1px solid #d1d5db;padding:9px;text-align:left;}\n")
                .append("    th{background:#f3f4f6;}\n")
                .append("    .forms{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:12px;}\n")
                .append("    label{display:block;font-size:.9rem;margin-bottom:4px;color:#334155;}\n")
                .append("    input{width:100%;padding:8px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box;}\n")
                .append("    button{margin-top:10px;background:#0f766e;color:#fff;border:none;border-radius:6px;padding:8px 12px;cursor:pointer;}\n")
                .append("    button:hover{background:#115e59;}\n")
                .append("  </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <h1>Web Appointment Manager</h1>\n")
                .append("  <div class=\"meta\">Server time: ").append(escapeHtml(serverTime)).append("</div>\n");

        if (feedbackMessage != null && !feedbackMessage.isEmpty()) {
            sb.append("  <div class=\"status\">").append(escapeHtml(feedbackMessage)).append("</div>\n");
        }

        if (searchResult != null) {
            sb.append("  <div class=\"panel\">\n")
                    .append("    <h2>Search Result</h2>\n")
                    .append("    <p>ID: ").append(searchResult.getId()).append("</p>\n")
                    .append("    <p>Date: ").append(escapeHtml(searchResult.getDate().toString())).append("</p>\n")
                    .append("    <p>Time: ").append(escapeHtml(searchResult.getTime().toString())).append("</p>\n")
                    .append("    <p>With: ").append(escapeHtml(searchResult.getWithWhom())).append("</p>\n")
                    .append("  </div>\n");
        }

        sb.append("  <div class=\"panel\">\n")
                .append("    <h2>Appointments Database</h2>\n")
                .append("    <table>\n")
                .append("      <thead><tr><th>ID</th><th>Date</th><th>Time</th><th>With</th></tr></thead>\n")
                .append("      <tbody>\n");

        if (snapshot.isEmpty()) {
            sb.append("        <tr><td colspan=\"4\">No appointments found.</td></tr>\n");
        } else {
            for (Appointment appointment : snapshot) {
                sb.append("        <tr><td>").append(appointment.getId())
                        .append("</td><td>").append(escapeHtml(appointment.getDate().toString()))
                        .append("</td><td>").append(escapeHtml(appointment.getTime().toString()))
                        .append("</td><td>").append(escapeHtml(appointment.getWithWhom()))
                        .append("</td></tr>\n");
            }
        }

        sb.append("      </tbody>\n")
                .append("    </table>\n")
                .append("  </div>\n");

        sb.append("  <div class=\"forms\">\n")
                .append("    <div class=\"panel\">\n")
                .append("      <h2>Add Appointment</h2>\n")
                .append("      <form action=\"/add\" method=\"POST\">\n")
                .append("        <label>Date</label><input type=\"date\" name=\"date\" required>\n")
                .append("        <label>Time</label><input type=\"time\" step=\"1\" name=\"time\" required>\n")
                .append("        <label>With Whom</label><input type=\"text\" name=\"withWhom\" required>\n")
                .append("        <button type=\"submit\">Add</button>\n")
                .append("      </form>\n")
                .append("    </div>\n")
                .append("    <div class=\"panel\">\n")
                .append("      <h2>Edit Appointment</h2>\n")
                .append("      <form action=\"/edit\" method=\"POST\">\n")
                .append("        <label>ID</label><input type=\"number\" min=\"1\" name=\"id\" required>\n")
                .append("        <label>New Date</label><input type=\"date\" name=\"date\" required>\n")
                .append("        <label>New Time</label><input type=\"time\" step=\"1\" name=\"time\" required>\n")
                .append("        <label>New Name</label><input type=\"text\" name=\"withWhom\" required>\n")
                .append("        <button type=\"submit\">Edit</button>\n")
                .append("      </form>\n")
                .append("    </div>\n")
                .append("    <div class=\"panel\">\n")
                .append("      <h2>Delete Appointment</h2>\n")
                .append("      <form action=\"/delete\" method=\"POST\">\n")
                .append("        <label>ID</label><input type=\"number\" min=\"1\" name=\"id\" required>\n")
                .append("        <button type=\"submit\">Delete</button>\n")
                .append("      </form>\n")
                .append("    </div>\n")
                .append("    <div class=\"panel\">\n")
                .append("      <h2>Search Appointment</h2>\n")
                .append("      <form action=\"/search\" method=\"POST\">\n")
                .append("        <label>ID</label><input type=\"number\" min=\"1\" name=\"id\" required>\n")
                .append("        <button type=\"submit\">Search</button>\n")
                .append("      </form>\n")
                .append("    </div>\n")
                .append("  </div>\n")
                .append("</body>\n")
                .append("</html>\n");

        return sb.toString();
    }

    private static String resolveDataFilePath() {
        File primary = new File(PRIMARY_DATA_FILE);
        if (primary.exists()) {
            return PRIMARY_DATA_FILE;
        }
        return FALLBACK_DATA_FILE;
    }

    private static void loadAppointments() {
        synchronized (appointmentsLock) {
            appointments.clear();
            File file = new File(appointmentsFilePath);
            if (!file.exists()) {
                return;
            }

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String record;
                while ((record = br.readLine()) != null) {
                    if (record.trim().isEmpty()) {
                        continue;
                    }
                    String idStr = record.substring(0, record.indexOf("#"));
                    String dateStr = record.substring(record.indexOf("#") + 1, record.indexOf("@"));
                    String timeStr = record.substring(record.indexOf("@") + 1, record.indexOf(">"));
                    String withWhomStr = record.substring(record.indexOf(">") + 1, record.indexOf("<"));
                    Appointment appointment = new Appointment(Integer.parseInt(idStr), dateStr, timeStr, withWhomStr);
                    appointments.add(appointment);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveAppointments() {
        synchronized (appointmentsLock) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(appointmentsFilePath))) {
                for (Appointment appointment : appointments) {
                    bw.write(appointment.getTxtFormat());
                    bw.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static Appointment findAppointmentById(int id) {
        synchronized (appointmentsLock) {
            for (Appointment appointment : appointments) {
                if (appointment.getId() == id) {
                    return appointment;
                }
            }
            return null;
        }
    }

    private static int getNextAppointmentId() {
        synchronized (appointmentsLock) {
            int newId = 1;
            for (Appointment appointment : appointments) {
                if (appointment.getId() >= newId) {
                    newId = appointment.getId() + 1;
                }
            }
            return newId;
        }
    }

    private static boolean removeAppointmentById(int id) {
        synchronized (appointmentsLock) {
            for (int i = 0; i < appointments.size(); i++) {
                if (appointments.get(i).getId() == id) {
                    appointments.remove(i);
                    return true;
                }
            }
            return false;
        }
    }

    private static List<Appointment> getAppointmentsSnapshot() {
        synchronized (appointmentsLock) {
            return new ArrayList<>(appointments);
        }
    }

    private static String addAppointment(String date, String time, String withWhom) {
        if (isBlank(date) || isBlank(time) || isBlank(withWhom)) {
            return "Add failed: all fields are required.";
        }

        int newId = getNextAppointmentId();
        Appointment newAppointment = new Appointment(newId, date, normalizeTime(time), withWhom.trim());

        synchronized (appointmentsLock) {
            appointments.add(newAppointment);
        }
        saveAppointments();
        return "Appointment added successfully with ID: " + newId;
    }

    private static String deleteAppointment(String idInput) {
        if (isBlank(idInput)) {
            return "Delete failed: ID is required.";
        }

        int deleteId;
        try {
            deleteId = Integer.parseInt(idInput.trim());
        } catch (NumberFormatException e) {
            return "Delete failed: ID must be numeric.";
        }

        if (removeAppointmentById(deleteId)) {
            saveAppointments();
            return "Appointment " + deleteId + " deleted successfully.";
        }
        return "Appointment with ID " + deleteId + " not found.";
    }

    private static class SearchResponse {
        final String message;
        final Appointment appointment;

        SearchResponse(String message, Appointment appointment) {
            this.message = message;
            this.appointment = appointment;
        }
    }

    private static SearchResponse searchAppointment(String idInput) {
        if (isBlank(idInput)) {
            return new SearchResponse("Search failed: ID is required.", null);
        }

        int searchId;
        try {
            searchId = Integer.parseInt(idInput.trim());
        } catch (NumberFormatException e) {
            return new SearchResponse("Search failed: ID must be numeric.", null);
        }

        Appointment result = findAppointmentById(searchId);
        if (result == null) {
            return new SearchResponse("No appointment found with ID: " + searchId, null);
        }
        return new SearchResponse("Appointment found with ID: " + searchId, result);
    }

    private static String editAppointment(String idInput, String date, String time, String withWhom) {
        if (isBlank(idInput) || isBlank(date) || isBlank(time) || isBlank(withWhom)) {
            return "Edit failed: ID, date, time, and name are required.";
        }

        int editId;
        try {
            editId = Integer.parseInt(idInput.trim());
        } catch (NumberFormatException e) {
            return "Edit failed: ID must be numeric.";
        }

        Appointment appointment = findAppointmentById(editId);
        if (appointment == null) {
            return "Appointment with ID " + editId + " not found.";
        }

        synchronized (appointmentsLock) {
            appointment.setDate(date);
            appointment.setTime(normalizeTime(time));
            appointment.setWithWhom(withWhom.trim());
        }
        saveAppointments();
        return "Appointment " + editId + " updated successfully.";
    }

    private static String normalizeTime(String time) {
        String trimmed = time.trim();
        if (trimmed.length() == 5) return trimmed + ":00";
        return trimmed;
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        if (body == null || body.isEmpty()) return form;

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            String key;
            String value;
            if (eqIndex >= 0) {
                key = decodeFormComponent(pair.substring(0, eqIndex));
                value = decodeFormComponent(pair.substring(eqIndex + 1));
            } else {
                key = decodeFormComponent(pair);
                value = "";
            }
            form.put(key, value);
        }

        return form;
    }

    private static String decodeFormComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

}
