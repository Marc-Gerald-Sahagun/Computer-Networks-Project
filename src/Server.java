import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 1234;
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static int clientCounter = 0; // tracks how many clients are currently connected

    public static void main(String[] args) {
        // thread pool so multiple clients can connect at the same time (max 10)
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        System.out.println("Math Server started, running on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // keep listening for new connections forever
            while (true) {
                Socket clientSocket = serverSocket.accept(); // blocks until a client connects
                threadPool.submit(new ClientHandler(clientSocket)); // each client gets its own thread
            }
        } catch (IOException e) {
            System.err.println("Could not open a server on port " + PORT);
        } finally {
            threadPool.shutdown(); // clean up threads when the server stops
        }
    }

    // each client connection is handled by one of these
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader inFromClient;   // reads data coming from the client
        private PrintWriter outFromServer;     // sends data back to the client
        private String clientName;
        private LocalDateTime connectionTime; // used to calculate session duration on disconnect

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.connectionTime = LocalDateTime.now();
        }

        @Override
        public void run() {
            try {
                inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream())); 
                outFromServer = new PrintWriter(socket.getOutputStream(), true); 

                // first message from the client should be their name, e.g. "JOIN:Marc"
                String initialMessage = inFromClient.readLine();

                // when the server gets a valid JOIN message, register the client and let them in
                if (initialMessage != null && initialMessage.startsWith("JOIN:")) {
                    clientName = initialMessage.substring(5).trim(); // strip the "JOIN:" prefix
                    clientCounter++;
                    logConnection(clientName);

                    // let the client know the connection went through
                    outFromServer.println("ACK:Welcome " + clientName + "! Connection successful.");

                    // keep reading requests until client disconnects or sends CLOSE
                    String request;
                    while ((request = inFromClient.readLine()) != null) {
                        if (request.equals("CLOSE")) {
                            break;
                        }
                        handleMathRequest(request.trim());
                    }
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                closeConnection(); // always runs to ensure connection close
            }
        }

        // evaluate the expression and send back the result (or an error if it's invalid)
        private void handleMathRequest(String expression) {
            try {
                double result = evaluateExpression(expression);
                String resultStr = String.valueOf(result); // convert to string so we can send it over the socket
                outFromServer.println("RESULT:" + resultStr); // send result back to client
                logRequest(clientName, expression, resultStr);
            } catch (Exception e) {
                outFromServer.println("ERROR:Invalid expression");
                System.err.println("Error calculating expression: " + e.getMessage());
            }
        }

        // called when a new client joins
        private void logConnection(String id) {
            String timestamp = getCurrentTime();
            System.out.println("==============================================");
            System.out.println("[" + timestamp + "] Client CONNECTED");
            System.out.println("Client Name: " + id);
            System.out.println("Number of clients: " + clientCounter);
            System.out.println("IP Address: " + socket.getInetAddress().getHostAddress());
            System.out.println("==============================================");
            logActivity(id, "Connected from " + socket.getInetAddress());
        }

        // called after every successful calculation
        private void logRequest(String client, String request, String result) {
            String timestamp = getCurrentTime();
            System.out.println(">>> [" + timestamp + "] " + client + " requested: " + request + " | Result: " + result);
            logActivity(client, "Calculation completed: " + request + " = " + result);
        }

        // clean up streams and log the disconnect with session duration
        private void closeConnection() {
            try {
                long duration = java.time.Duration.between(connectionTime, LocalDateTime.now()).toSeconds();
                clientCounter--;

                System.out.println("==============================================");
                System.out.println("[" + getCurrentTime() + "] Client DISCONNECTED");
                System.out.println("Client Name: " + clientName);
                System.out.println("Number of clients: " + clientCounter);
                System.out.println("Connection Duration: " + duration + " seconds");
                System.out.println("==============================================");
                logActivity(clientName, "Disconnected. Session duration: " + duration + " seconds");

                // close everything — null checks in case the connection failed before they were opened
                if (inFromClient != null) inFromClient.close();
                if (outFromServer != null) outFromServer.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }

        private String getCurrentTime() {
            return LocalDateTime.now().format(dateFormatter);
        }
    }

    // appends a timestamped line to the log file
    private static void logActivity(String clientName, String activity) {
        // try-with-resources so the file closes automatically even if something goes wrong
        try (FileWriter fw = new FileWriter("server_log.txt", true); // true = append, not overwrite
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            String timestamp = LocalDateTime.now().format(dateFormatter);
            out.println("[" + timestamp + "] " + clientName + ": " + activity);

        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    // strip whitespace before passing to the parser
    private static double evaluateExpression(String expression) {
        expression = expression.replaceAll("\\s+", "");
        return evaluate(expression);
    }

    // use two stacks for operator precedence
    // one stack for numbers, one for operators
    private static double evaluate(String expression) {
        Stack<Double> numbers = new Stack<>();
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            // parse full number (including decimals like 3.14)
            if (Character.isDigit(c) || c == '.') {
                StringBuilder num = new StringBuilder();
                while (i < expression.length() &&
                        (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    num.append(expression.charAt(i++));
                }
                i--; // back up one since the loop will increment again
                numbers.push(Double.parseDouble(num.toString()));
            }
            else if (c == '(') {
                operators.push(c); // push and wait until we hit the closing paren
            }
            else if (c == ')') {
                // apply everything back to the matching open paren
                while (operators.peek() != '(') {
                    numbers.push(applyOperation(operators.pop(), numbers.pop(), numbers.pop()));
                }
                operators.pop(); // discard the '('
            }
            else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                // negative: handles cases like -3, (-5), or 2 * -1
                if (c == '-' && (i == 0 || expression.charAt(i - 1) == '(' ||
                        "+-*/%".indexOf(expression.charAt(i - 1)) >= 0)) {
                    numbers.push(-1.0);
                    operators.push('*'); // treat it as multiplying by -1
                } else {
                    // apply any higher precedence operators sitting on the stack first
                    while (!operators.isEmpty() && operators.peek() != '(' && hasPrecedence(c, operators.peek())) {
                        numbers.push(applyOperation(operators.pop(), numbers.pop(), numbers.pop()));
                    }
                    operators.push(c);
                }
            }
        }

        // apply whatever's left on the stack
        while (!operators.isEmpty()) {
            numbers.push(applyOperation(operators.pop(), numbers.pop(), numbers.pop()));
        }

        return numbers.pop();
    }

    // returns false if op1 should be pushed without applying op2 first (i.e. op1 has higher precedence)
    private static boolean hasPrecedence(char op1, char op2) {
        if ((op1 == '*' || op1 == '/' || op1 == '%') && (op2 == '+' || op2 == '-')) return false;
        return true;
    }

    // note: a and b are swapped on purpose because of how they come off the stack
    private static double applyOperation(char op, double b, double a) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new ArithmeticException("Division by zero");
                return a / b;
            case '%':
                if (b == 0) throw new ArithmeticException("Division by zero");
                return a % b;
        }
        return 0;
    }
}