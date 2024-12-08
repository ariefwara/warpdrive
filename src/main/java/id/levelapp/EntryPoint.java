package id.levelapp;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * EntryPoint class acts as the entry point for the HTTP proxy server.
 * It listens for client connections and optionally forwards requests to exit points.
 */
public class EntryPoint {
    /** Port for clients to connect to the proxy server. */
    public static int PROXY_PORT = 8010;
    /** Port for ExitPoints to connect to the proxy server. */
    public static int EXIT_SERVER_PORT = 1080;
    /** Forward mode: true if requests should be forwarded to ExitPoints, false for direct connection. */
    public static boolean IS_FORWARD = true;

    private static final ConcurrentLinkedQueue<Socket> exitPoints = new ConcurrentLinkedQueue<>();
    private static int roundRobinIndex = 0;

    /**
     * Starts the HTTP proxy server.
     * 
     * @param args Command-line arguments.
     */
    public static void start(String[] args) {
        System.out.println("Starting HTTP Proxy on port " + PROXY_PORT);

        if (IS_FORWARD) {
            new Thread(EntryPoint::listenForExitPoints).start(); // Listen for ExitPoints
        }

        new Thread(EntryPoint::listenForClients).start(); // Listen for Clients
    }

    /**
     * Listens for connections from ExitPoints.
     */
    private static void listenForExitPoints() {
        try (ServerSocket serverSocket = new ServerSocket(EXIT_SERVER_PORT)) {
            System.out.println("Listening for ExitPoints on port " + EXIT_SERVER_PORT);
            while (true) {
                Socket exitSocket = serverSocket.accept();
                System.out.println("New ExitPoint connected: " + exitSocket.getRemoteSocketAddress());
                exitPoints.add(exitSocket);
                new Thread(() -> monitorExitPointConnection(exitSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Error in ExitPoint connection: " + e.getMessage());
        }
    }

    /**
     * Monitors the connection to an ExitPoint and removes it from the list when disconnected.
     * 
     * @param socket The socket connection to the ExitPoint.
     */
    private static void monitorExitPointConnection(Socket socket) {
        try {
            socket.getInputStream().read(); // Block until socket is closed
        } catch (IOException ignored) {}
        System.out.println("ExitPoint disconnected: " + socket.getRemoteSocketAddress());
        exitPoints.remove(socket);
    }

    /**
     * Listens for client connections and handles them using ProxyHandler.
     */
    private static void listenForClients() {
        try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("Listening for Clients on port " + PROXY_PORT);
            while (true) {
                Socket clientSocket = proxySocket.accept();
                new Thread(new ProxyHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Error listening for clients: " + e.getMessage());
        }
    }

    /**
     * ProxyHandler class handles individual client connections.
     */
    static class ProxyHandler implements Runnable {
        private final Socket clientSocket;

        /**
         * Constructs a ProxyHandler for a given client socket.
         * 
         * @param clientSocket The client socket.
         */
        public ProxyHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (
                InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
            ) {
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty()) return;

                System.out.println("Request: " + requestLine);
                String[] parts = requestLine.split(" ");
                if ("CONNECT".equalsIgnoreCase(parts[0])) {
                    if (IS_FORWARD) {
                        forwardToExitPoint(parts[1], clientInput, clientOutput);
                    } else {
                        connectDirectly(parts[1], clientInput, clientOutput);
                    }
                } else {
                    System.out.println("Unsupported HTTP method: " + parts[0]);
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        /**
         * Connects directly to the target server and forwards data between the client and server.
         * 
         * @param target The target server address in the format "host:port".
         * @param clientInput The input stream from the client.
         * @param clientOutput The output stream to the client.
         */
        private void connectDirectly(String target, InputStream clientInput, OutputStream clientOutput) {
            String[] hostPort = target.split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            System.out.println("Connecting directly to target: " + host + ":" + port);

            try (Socket serverSocket = new Socket(host, port)) {
                serverSocket.setTcpNoDelay(true);
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                clientOutput.flush();

                Thread clientToServer = new Thread(() -> {
                    try {
                        forwardData(clientInput, serverSocket.getOutputStream(), "clientToServer");
                    } catch (IOException e) {
                        System.err.println("Error in clientToServer: " + e.getMessage());
                    }
                });

                Thread serverToClient = new Thread(() -> {
                    try {
                        forwardData(serverSocket.getInputStream(), clientOutput, "serverToClient");
                    } catch (IOException e) {
                        System.err.println("Error in serverToClient: " + e.getMessage());
                    }
                });

                clientToServer.start();
                serverToClient.start();
                clientToServer.join();
                serverToClient.join();
            } catch (Exception e) {
                System.err.println("Error connecting directly to target: " + e.getMessage());
            }
        }

        /**
         * Forwards the client request to an ExitPoint and relays data between the client and ExitPoint.
         * 
         * @param target The target server address in the format "host:port".
         * @param clientInput The input stream from the client.
         * @param clientOutput The output stream to the client.
         */
        private void forwardToExitPoint(String target, InputStream clientInput, OutputStream clientOutput) {
            Socket exitSocket = getNextExitPoint();
            if (exitSocket == null) {
                System.err.println("No active ExitPoint connections.");
                try {
                    clientOutput.write("HTTP/1.1 502 Bad Gateway\r\n\r\nNo ExitPoint available.".getBytes());
                    clientOutput.flush();
                } catch (IOException ignored) {}
                return;
            }

            System.out.println("Forwarding to ExitPoint: " + exitSocket.getRemoteSocketAddress());
            try {
                PrintWriter exitWriter = new PrintWriter(exitSocket.getOutputStream(), true);
                exitWriter.println(target); // Send target to ExitPoint

                Thread clientToExit = new Thread(() -> {
                    try {
                        forwardData(clientInput, exitSocket.getOutputStream(), "Entry -> Exit Point");
                    } catch (IOException e) {
                        System.err.println("Error in Entry -> Exit Point: " + e.getMessage());
                    }
                });

                Thread exitToClient = new Thread(() -> {
                    try {
                        forwardData(exitSocket.getInputStream(), clientOutput, "Exit -> Entry Point");
                    } catch (IOException e) {
                        System.err.println("Error in Exit -> Entry Point: " + e.getMessage());
                    }
                });

                clientToExit.start();
                exitToClient.start();
                clientToExit.join();
                exitToClient.join();
            } catch (Exception e) {
                System.err.println("Error forwarding to ExitPoint: " + e.getMessage());
            }
        }

        /**
         * Retrieves the next available ExitPoint using a round-robin strategy.
         * 
         * @return The next available ExitPoint socket, or null if none are available.
         */
        private Socket getNextExitPoint() {
            synchronized (exitPoints) {
                if (exitPoints.isEmpty()) return null;

                int size = exitPoints.size();
                Socket[] exitArray = exitPoints.toArray(new Socket[0]);
                Socket selected = exitArray[roundRobinIndex % size];
                roundRobinIndex = (roundRobinIndex + 1) % size;

                if (selected.isClosed()) {
                    exitPoints.remove(selected);
                    return getNextExitPoint();
                }
                return selected;
            }
        }

        /**
         * Forwards data from an input stream to an output stream.
         * 
         * @param input The input stream to read data from.
         * @param output The output stream to write data to.
         * @param direction A string indicating the direction of data flow for logging purposes.
         * @throws IOException If an I/O error occurs.
         */
        private void forwardData(InputStream input, OutputStream output, String direction) throws IOException {
            byte[] buffer = new byte[32768];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
            System.out.println(direction + " completed.");
        }
    }
}
