import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        try (ServerSocket welcomeSocket = new ServerSocket(1234)) {
            System.out.println("Server is online!");
            logActivity("SERVER", "Server started");

            while (true) {
                Socket connectionSocket = welcomeSocket.accept();
                threadPool.submit(() -> handleClient(connectionSocket));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            threadPool.shutdown();
        }
    }

    public static void handleClient(Socket connectionSocket) {
        String clientName = null;
        LocalDateTime connectTime = LocalDateTime.now();

        try (BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
             PrintWriter outFromServer = new PrintWriter(connectionSocket.getOutputStream(), true)) {

            String sentence;
            while ((sentence = inFromClient.readLine()) != null) {

                if (sentence.startsWith("JOIN:")) {
                    clientName = sentence.substring(5).trim();

                    outFromServer.println("ACK:Welcome " + clientName + "! Connection successful.");
                    logActivity(clientName, "Connected from " + connectionSocket.getInetAddress());
                    System.out.println("Client: " + clientName + " (" + connectionSocket.getInetAddress() + ") connected!");

                }
                else if (sentence.equals("CLOSE")) {
                    if (clientName != null) {
                        LocalDateTime disconnectTime = LocalDateTime.now();
                        long duration = java.time.Duration.between(connectTime, disconnectTime).toSeconds();
                        outFromServer.println("ACK:Connection closed. Goodbye " + clientName + "!");
                        logActivity(clientName, "Disconnected. Session duration: " + duration + " seconds");
                        System.out.println("Client " + clientName + " disconnected. Duration: " + duration + " seconds");
                        break;
                    }
                }
                else {
                    if (clientName != null) {
                        String expression = sentence.trim();

                        logActivity(clientName, "Sent calculation request: " + expression);
                        System.out.println("Message received from client " + clientName + ": " + expression);

                        try {
                            double result = evaluateExpression(expression);
                            outFromServer.println("RESULT:" + result);
                            logActivity(clientName, "Calculation completed: " + expression + " = " + result);
                        } catch (Exception e) {
                            outFromServer.println("ERROR:Invalid expression");
                            logActivity(clientName, "Invalid expression: " + expression);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Exception: Client connection error - " + e.getMessage());
        } finally {
            try {
                connectionSocket.close();
            } catch (Exception ignored) {}
        }
    }

    private static double evaluateExpression(String expression) {
        expression = expression.replaceAll("\\s+", "");
        return evaluate(expression);
    }

    private static double evaluate(String expression) {
        Stack<Double> numbers = new Stack<>();
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                StringBuilder num = new StringBuilder();
                while (i < expression.length() &&
                        (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    num.append(expression.charAt(i++));
                }
                i--;
                numbers.push(Double.parseDouble(num.toString()));
            }
            else if (c == '(') {
                operators.push(c);
            }
            else if (c == ')') {
                while (operators.peek() != '(') {
                    numbers.push(applyOperation(operators.pop(), numbers.pop(), numbers.pop()));
                }
                operators.pop();
            }
            else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                // Detect unary minus: occurs at start, after '(', or after another operator
                if (c == '-' && (i == 0 || expression.charAt(i - 1) == '(' ||
                        "+-*/%".indexOf(expression.charAt(i - 1)) >= 0)) {
                    numbers.push(-1.0);
                    operators.push('*');
                } else {
                    while (!operators.isEmpty() && operators.peek() != '(' && hasPrecedence(c, operators.peek())) {
                        numbers.push(applyOperation(operators.pop(), numbers.pop(), numbers.pop()));
                    }
                    operators.push(c);
                }
            }
        }

        while (!operators.isEmpty()) {
            numbers.push(applyOperation(operators.pop(), numbers.pop(), numbers.pop()));
        }

        return numbers.pop();
    }

    private static boolean hasPrecedence(char op1, char op2) {
        if ((op1 == '*' || op1 == '/' || op1 == '%') && (op2 == '+' || op2 == '-')) return false;
        return true;
    }

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

    private static void logActivity(String clientName, String activity) {
        try (FileWriter fw = new FileWriter("server_log.txt", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            String timestamp = LocalDateTime.now().format(dateFormatter);
            out.println("[" + timestamp + "] " + clientName + ": " + activity);

        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}