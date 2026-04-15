import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter your name: ");
        String clientName = scanner.nextLine();

        try (Socket clientConnection = new Socket("localhost", 1234);
             BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
             PrintWriter outToServer = new PrintWriter(clientConnection.getOutputStream(), true)) {

            // Step 1: Send JOIN request and wait for acknowledgement
            outToServer.println("JOIN:" + clientName);

            String response = inFromServer.readLine();
            if (response != null && response.startsWith("ACK:")) {
                System.out.println("Server: " + response.substring(4));
            }

            // Thread to receive server responses
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = inFromServer.readLine()) != null) {
                        if (serverMessage.startsWith("RESULT:")) {
                            System.out.println("Result: " + serverMessage.substring(7));
                        } else if (serverMessage.startsWith("ERROR:")) {
                            System.out.println("Error: " + serverMessage.substring(6));
                        } else if (serverMessage.startsWith("ACK:")) {
                            System.out.println("Server: " + serverMessage.substring(4));
                        }
                    }
                } catch (IOException e) {
                    // Connection closed
                }
            }).start();

            // Step 2: Send calculations
            System.out.println("\nEnter calculations (type 'exit' to disconnect):");
            String input;
            while (scanner.hasNextLine()) {
                input = scanner.nextLine();
                if (input.equalsIgnoreCase("exit")) {
                    break;
                }
                // Send calculation (just the expression, no "CALC:" prefix needed)
                outToServer.println(input);
            }

            // Step 3: Send CLOSE request
            outToServer.println("CLOSE");
            Thread.sleep(500); // Wait for final acknowledgement

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        }

        scanner.close();
    }
}