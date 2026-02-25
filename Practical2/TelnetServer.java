//Abdelrahman Ahmed u24898008 && Hamdaan Mirza 24631494

import java.net.*;
import java.io.*;
import java.sql.Date;
import java.sql.Time;

public class TelnetServer {

    private static class Appointment {
        int id;
        Date date;
        Time time;
        String withWhom;

        public Appointment() {
            id = 0;
            date = Date.valueOf("1970-01-01");
            time = Time.valueOf("00:00:00");
            withWhom = "Nobody";
        }

        //public Appointment(int id, Date date, Time time, )
    }

        public static void main(String[] args) {
            // choose port number
            int port = 2323;

            // open server socket
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Server listenin on port" + port);

                // open client socket and await connection
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // open input and output streams
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);

                // welcome message
                output.println("Welcome to the Telnet Server!");

                // keep connection alive until client disconnects
                String line;
                while ((line = input.readLine()) != null) {
                    // clean any extra spaces/newlines sent from telnet
                    line = line.trim();

                    System.out.println(line);

                    // check if the client wants to quit
                    if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                        // exit the waiting loop
                        output.println("Goodbye!");
                        break;
                    }

                    // echo input
                    output.println("Client:" + line);

                    // send output

                    // close input and output streams
                    input.close();
                    output.close();
                    // close client socket
                    clientSocket.close();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
}