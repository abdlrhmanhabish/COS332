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
            return "ID: " + getId() + "\n" + "Date: " + getDate() + "\n" +
                   "Time: " + getTime() + "\n" + "Staff: " + getWithWhom() + "\n";
        }

        // Print appointment details to PrintWriter (for telnet output)
        public void printTo(PrintWriter out) {
            out.print("ID: " + getId() + "\r\n");
            out.print("Date: " + getDate() + "\r\n");
            out.print("Time: " + getTime() + "\r\n");
            out.print("Staff: " + getWithWhom() + "\r\n");
            out.flush();
        }

        // Getters
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

        // Setters
        public void setId(int id) {
            this.id = id;
        }

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

    // Helper method to print to telnet client with automatic flush
    private static void telnetPrint(PrintWriter out, String message) {
        out.print(message + "\r\n");
        out.flush();
    }

    // Helper method to find appointment by ID
    private static Appointment findAppointmentById(ArrayList<Appointment> appointments, int id) {
        for (Appointment a : appointments) {
            if (a.getId() == id) return a;
        }
        return null;
    }

    // Helper method to get next available appointment ID
    private static int getNextAppointmentId(ArrayList<Appointment> appointments) {
        int newId = 1;
        for (Appointment a : appointments) {
            if (a.getId() >= newId) newId = a.getId() + 1;
        }
        return newId;
    }

    // Helper method to remove appointment by ID
    private static boolean removeAppointmentById(ArrayList<Appointment> appointments, int id) {
        for (int i = 0; i < appointments.size(); i++) {
            if (appointments.get(i).getId() == id) {
                appointments.remove(i);
                return true;
            }
        }
        return false;
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
                    Appointment appointment = new Appointment(Integer.parseInt(idStr), dateStr, timeStr, withWhomStr);
                    appointments.add(appointment);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // welcome message
            telnetPrint(output, "Welcome to the Telnet Server!");

            // keep connection alive until client disconnects and respond to requests
            String line;
            while ((line = input.readLine()) != null) {
                // clean any extra spaces/newlines sent from telnet
                line = line.trim();

                System.out.println(line); // debugging

                // echo input
                output.print(line + "\r\n");
                output.flush();

                // quit command to close connection
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    output.print("Goodbye!\r\n");
                    output.flush();
                    break;
                }

                // list command to show all appointments
                else if (line.equalsIgnoreCase("list")) {
                    if (appointments.isEmpty()) {
                        output.print("No appointments found.\r\n");
                        output.flush();
                    } else {
                        output.print("Appointments: \r\n");
                        output.flush();
                        for (Appointment a : appointments) a.printTo(output);
                    }
                }

                // add command to create a new appointment
                else if (line.equalsIgnoreCase("add")) {
                    try {
                        // Creating ID for appointment using helper method
                        int newId = getNextAppointmentId(appointments);

                        // Entering relevant details
                        output.print("Enter date (YYYY-MM-DD):\r\n");
                        output.flush();
                        String dateInput = input.readLine();
                        if (dateInput == null) break;
                        dateInput = dateInput.trim();

                        output.print("Enter time (HH:MM:SS):\r\n");
                        output.flush();
                        String timeInput = input.readLine();
                        if (timeInput == null) break;
                        timeInput = timeInput.trim();

                        output.print("Enter name of Staff:\r\n");
                        output.flush();
                        String withWhomInput = input.readLine();
                        if (withWhomInput == null) break;
                        withWhomInput = withWhomInput.trim();

                        // add new appointment
                        Appointment newAppointment = new Appointment(newId, dateInput, timeInput, withWhomInput);
                        appointments.add(newAppointment);

                        output.print("Appointment added successfully with ID: " + newId + "\r\n");
                        output.flush();
                    } catch (IllegalArgumentException e) {
                        output.print("Error: Wrong date or time format. Please use YYYY-MM-DD for date and HH:MM:SS for time.\r\n");
                        output.flush();
                        output.flush();
                    }
                }

                // delete command-removes appointment by ID number
                else if (line.equalsIgnoreCase("delete")) {
                    if (appointments.isEmpty()) {
                        output.print("No appointments to delete.\r\n");
                        output.flush();
                    } else {
                        output.print("Enter the ID of the appointment to delete:\r\n");
                        output.flush();
                        String idInput = input.readLine();
                        if (idInput == null) break;
                        idInput = idInput.trim();

                        try {
                            int deleteId = Integer.parseInt(idInput);
                            if (removeAppointmentById(appointments, deleteId)) {
                                output.print("Appointment " + deleteId + " deleted successfully.\r\n");
                                output.flush();
                            } else {
                                output.print("Appointment with ID " + deleteId + " not found.\r\n");
                                output.flush();
                            }
                        } catch (NumberFormatException e) {
                            output.print("Error: Please enter a valid numeric ID.\r\n");
                            output.flush();
                        }
                    }
                }

                // search command to find appointments
                else if (line.equalsIgnoreCase("search")) {
                    output.print("Enter the appointment ID you are looking for:\r\n");
                    output.flush();
                    output.flush();
                    String idInput = input.readLine();
                    if (idInput == null) break;
                    idInput = idInput.trim();

                    try {
                        int searchId = Integer.parseInt(idInput);
                        Appointment result = findAppointmentById(appointments, searchId);
                        
                        if (result == null) {
                            output.print("No appointment found with ID: " + searchId + "\r\n");
                            output.flush();
                        } else {
                            output.print("Appointment found:\r\n");
                            output.flush();
                            result.printTo(output);
                        }
                    } catch (NumberFormatException e) {
                        output.print("Error: Please enter a valid numeric ID.\r\n");
                        output.flush();
                    }
                }

                // edit command to edit an existing appointment
                else if (line.equalsIgnoreCase("edit")) {
                    if (appointments.isEmpty()) {
                        output.print("No appointments to edit.\r\n");
                        output.flush();
                    } else {
                        output.print("Enter the ID of the appointment to edit:\r\n");
                        output.flush();
                        String idInput = input.readLine();
                        if (idInput == null) break;
                        idInput = idInput.trim();

                        try {
                            int editId = Integer.parseInt(idInput);
                            Appointment appointmentToEdit = findAppointmentById(appointments, editId);

                            if (appointmentToEdit == null) {
                                output.print("Appointment with ID " + editId + " not found.\r\n");
                                output.flush();
                            } else {
                                output.print("Appointment found:\r\n");
                                output.flush();
                                appointmentToEdit.printTo(output);
                                
                                output.print("What would you like to edit?\r\n");
                                output.print("  1 - Date (YYYY-MM-DD) of appointment\r\n");
                                output.print("  2 - Time (HH:MM:SS) of appointment\r\n");
                                output.print("  3 - Your appointment with (staff name)\r\n");
                                output.print("  0 - Cancel\r\n");
                                output.print("Enter your choice:\r\n");
                                output.flush();
                                
                                String choice = input.readLine();
                                if (choice == null) break;
                                choice = choice.trim();

                                if (choice.equals("1")) {
                                    output.print("Enter new date (YYYY-MM-DD):\r\n");
                                    output.flush();
                                    String newDate = input.readLine();
                                    if (newDate == null) break;
                                    newDate = newDate.trim();
                                    appointmentToEdit.setDate(newDate);
                                    output.print("Date updated successfully.\r\n");
                                    output.flush();
                                } else if (choice.equals("2")) {
                                    output.print("Enter new time (HH:MM:SS):\r\n");
                                    output.flush();
                                    String newTime = input.readLine();
                                    if (newTime == null) break;
                                    newTime = newTime.trim();
                                    appointmentToEdit.setTime(newTime);
                                    output.print("Time updated successfully.\r\n");
                                    output.flush();
                                } else if (choice.equals("3")) {
                                    output.print("Enter new staff name:\r\n");
                                    output.flush();
                                    String newName = input.readLine();
                                    if (newName == null) break;
                                    newName = newName.trim();
                                    appointmentToEdit.setWithWhom(newName);
                                    output.print("Staff name updated successfully.\r\n");
                                    output.flush();
                                } else if (choice.equals("0")) {
                                    output.print("Edit cancelled.\r\n");
                                    output.flush();
                                } else {
                                    output.print("Wrong choice.\r\n");
                                    output.flush();
                                }
                            }
                        } catch (NumberFormatException e) {
                            output.print("Error: Please enter a valid numeric ID.\r\n");
                            output.flush();
                        } catch (IllegalArgumentException e) {
                            output.print("Error: Wrong date or time format. Please use YYYY-MM-DD for date and HH:MM:SS for time.\r\n");
                            output.flush();
                        }
                    }
                }

                // help command to show available commands
                else if (line.equalsIgnoreCase("help")) {
                    output.print("What do you need help with? Here are the available commands:\r\n");
                    output.print("  list   - View all appointments\r\n");
                    output.print("  add    - Add a new appointment\r\n");
                    output.print("  search - Search appointment by ID\r\n");
                    output.print("  edit   - Edit an appointment by ID\r\n");
                    output.print("  delete - Delete an appointment by ID\r\n");
                    output.print("  help   - Show this help message\r\n");
                    output.print("  exit   - Disconnect from server\r\n");
                    output.flush();
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