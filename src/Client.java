import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Scanner;
import java.net.Socket;

public class Client{
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("What is your name?");
        String name = scanner.nextLine();
        System.out.println("What port number do you want to connect to?");
        int portNum = scanner.nextInt();
        try(Socket clientSocket = new Socket("localhost", portNum)) {
            System.out.println("Client started: Enter a message");
            // Set up streams
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter outFromClient = new PrintWriter(clientSocket.getOutputStream(), true);

            String sentence;
            while(!(sentence = inFromUser.readLine()).equalsIgnoreCase("exit")) {
                outFromClient.println(name + ": " + sentence);
                System.out.println("Response from server: " + inFromServer.readLine());
                System.out.println("Enter a new message: ");
            }
            System.out.println("Exiting...");

        } catch (Exception e) {
            System.err.println("Exception error with client");
        }
    }
}