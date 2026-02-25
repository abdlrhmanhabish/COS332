//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.net.*;
import java.io.*;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;

public class TelnetServer {

    private static class Appointment {
        private int id;
        private Date date;
        private Time time;
        private String withWhom;

        public Appointment() {
            id = 0;
            date = Date.valueOf("1970-01-01");
            time = Time.valueOf("00:00:00");
            withWhom = "Nobody";
        }

        public Appointment(int id, String date, String time, String withWhom) {
            this.id = id;
            this.date = Date.valueOf(date);
            this.time = Time.valueOf(time);
            this.withWhom = withWhom;
        }

        public String print() {
            String output;
            output = "Number: " + id + "\nDate: " + date + "\nTime: " + time + "\nwith: " + withWhom + "\n";
            return output;
        }

        // add the setters and getters for editing and searching
    }

    public static void main(String[] args) {
        // choose port number
        int port = 2323;

        // open server socket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listenin on port" + port); // debugging

            // open client socket and await connection
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress()); // debugging

            // open input and output streams
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);

            // load the appointments from database (textfile) into an arraylist
            ArrayList<Appointment> appointments = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader("appointments.txt"))) {
                String record;
                while ((record = br.readLine()) != null) {
                    String idStr = record.substring(0, record.indexOf("#"));
                    String dateStr = record.substring(record.indexOf("#") + 1, record.indexOf("@"));
                    String timeStr = record.substring(record.indexOf("@") + 1, record.indexOf(">"));
                    String withWhomStr = record.substring(record.indexOf(">") + 1, record.indexOf("<"));
                    int id = Integer.parseInt(idStr);
                    String withwhom = withWhomStr.replace("_", "");
                    Appointment appointment = new Appointment(id, dateStr, timeStr, withwhom);
                    appointments.add(appointment);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // welcome message
            output.println("Welcome to the Telnet Server!");

            // keep connection alive until client disconnects and respond to requests
            String line;
            while ((line = input.readLine()) != null) {
                // clean any extra spaces/newlines sent from telnet
                line = line.trim();

                System.out.println(line); // debugging

                // echo input
                output.println(line);

                // quit command to close connection
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    output.println("Goodbye!");
                    break;
                }

                // list command to show all appointments
                else if (line.equalsIgnoreCase("list")) {
                    for (Appointment a : appointments) {
                        output.println(a.print());
                    }
                }

            }

            // close input and output streams
            input.close();
            output.close();

            // close client socket
            clientSocket.close();

        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}