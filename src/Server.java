import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static void main (String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        try(ServerSocket welcomeSocket = new ServerSocket(1234)) {
            System.out.println("Server is online!");
            while(true) {
                Socket connectionSocket = welcomeSocket.accept();
                threadPool.submit(() -> handleClient(connectionSocket));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally{
            threadPool.shutdown();
        }
    }
    public static void handleClient(Socket connectionSocket) {
        try(BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            PrintWriter outFromServer = new PrintWriter(connectionSocket.getOutputStream(), true);) {
            System.out.println("Client: " + connectionSocket.getInetAddress() + " connected!");

            String sentence;
            while((sentence = inFromClient.readLine()) != null && !sentence.equalsIgnoreCase("exit")) {
                System.out.println(sentence);
                outFromServer.println(sentence.toUpperCase());
            }
            System.out.println("Client " + connectionSocket.getInetAddress() + " disconnected." );

        } catch (Exception e) {
            System.err.println("Exception: Client connection error");
        }
        finally {
            try{
                connectionSocket.close();
            }
            catch (Exception ignored) {}
        }

    }
}