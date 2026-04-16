import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter your name: ");
        String clientName = scanner.nextLine();

        // connect to the server — streams auto-close when the try block exits
        try (Socket clientConnection = new Socket("localhost", 1234);
             BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
             PrintWriter outToServer = new PrintWriter(clientConnection.getOutputStream(), true)) { // true = auto-flush

            // Step Send JOIN request and wait for acknowledgement
            outToServer.println("JOIN:" + clientName);

            String response = inFromServer.readLine();
            if (response != null && response.startsWith("ACK:")) {
                System.out.println("Server: " + response.substring(4)); // strip the "ACK:" prefix before printing
            }

            // separate thread to listen for server responses so it doesn't block user input
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = inFromServer.readLine()) != null) {
                        if (serverMessage.startsWith("RESULT:")) {
                            System.out.println("Result: " + serverMessage.substring(7)); // strip "RESULT:"
                        } else if (serverMessage.startsWith("ERROR:")) {
                            System.out.println("Error: " + serverMessage.substring(6)); // strip "ERROR:"
                        } else if (serverMessage.startsWith("ACK:")) {
                            System.out.println("Server: " + serverMessage.substring(4)); // strip "ACK:"
                        }
                    }
                } catch (IOException e) {
                    // connection closed, thread exits naturally
                }
            }).start();

            // Send calculations
            System.out.println("\nEnter calculations (type 'exit' to disconnect):");
            String input;
            while (scanner.hasNextLine()) {
                input = scanner.nextLine();
                if (input.equalsIgnoreCase("exit")) {
                    break;
                }
                outToServer.println(input); // send the expression directly, server handles the parsing
            }

            // Send CLOSE request
            outToServer.println("CLOSE");
            Thread.sleep(500); // give the server a moment to send back the final ACK before closing

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }
}