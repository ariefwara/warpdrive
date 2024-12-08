package id.levelapp;

import java.io.*;
import java.net.*;

/**
 * The ExitPoint class represents the exit point in the Warpdrive application.
 * It establishes connections to the EntryPoint and forwards requests to target servers.
 */
public class ExitPoint {
    /** The host address of the EntryPoint. */
    public static String ENTRY_HOST = "localhost"; // Replace with EntryPoint's IP
    /** The port number of the EntryPoint. */
    public static int ENTRY_PORT = 1080;
    /** The number of concurrent connections to the EntryPoint. */
    public static int CONNECTION_COUNT = 10;

    /**
     * Starts the ExitPoint with the specified number of connections.
     *
     * @param args Command-line arguments.
     */
    public static void start(String[] args) {
        System.out.println("Starting ExitPoint with " + CONNECTION_COUNT + " connections...");

        for (int i = 0; i < CONNECTION_COUNT; i++) {
            new Thread(new ExitPointWorker(ENTRY_HOST, ENTRY_PORT, i + 1)).start();
        }
    }

    /**
     * The ExitPointWorker class handles the connection to the EntryPoint and forwards requests to target servers.
     */
    static class ExitPointWorker implements Runnable {
        private final String entryHost;
        private final int entryPort;
        private final int workerId;

        /**
         * Constructs an ExitPointWorker with the specified parameters.
         *
         * @param entryHost The host address of the EntryPoint.
         * @param entryPort The port number of the EntryPoint.
         * @param workerId  The unique identifier for this worker.
         */
        public ExitPointWorker(String entryHost, int entryPort, int workerId) {
            this.entryHost = entryHost;
            this.entryPort = entryPort;
            this.workerId = workerId;
        }

        /**
         * Runs the worker, establishing a connection to the EntryPoint and forwarding requests.
         */
        @Override
        public void run() {
            while (true) {
                try (Socket entrySocket = new Socket(entryHost, entryPort);
                     InputStream entryInput = entrySocket.getInputStream();
                     OutputStream entryOutput = entrySocket.getOutputStream();
                     BufferedReader entryReader = new BufferedReader(new InputStreamReader(entryInput))) {

                    System.out.println("Worker " + workerId + " connected to Entry Point: " + entryHost + ":" + entryPort);

                    String target;
                    while ((target = entryReader.readLine()) != null && !target.isEmpty()) {
                        System.out.println("Worker " + workerId + " forwarding to target: " + target);
                        forwardToTarget(target, entryInput, entryOutput);
                    }
                } catch (IOException e) {
                    System.err.println("Worker " + workerId + " disconnected due to IOException: " + e.getMessage());
                    System.out.println("Worker " + workerId + " will retry connection in 5 seconds...");
                    try {
                        Thread.sleep(5000); // Retry after 5 seconds
                    } catch (InterruptedException ignored) {
                        System.out.println("Worker " + workerId + " sleep interrupted.");
                    }
                }
            }
        }

        /**
         * Forwards the request to the specified target server.
         *
         * @param target      The target server address in the format "host:port".
         * @param entryInput  The input stream from the EntryPoint.
         * @param entryOutput The output stream to the EntryPoint.
         */
        private void forwardToTarget(String target, InputStream entryInput, OutputStream entryOutput) {
            String[] hostPort = target.split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            System.out.println("Worker " + workerId + " connecting directly to target: " + host + ":" + port);

            try (Socket serverSocket = new Socket(host, port)) {
                serverSocket.setTcpNoDelay(true);
                entryOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                entryOutput.flush();

                Thread clientToServer = new Thread(() -> {
                    try {
                        forwardData(entryInput, serverSocket.getOutputStream());
                    } catch (IOException e) {
                        System.err.println("Worker " + workerId + " error in clientToServer: " + e.getMessage());
                    }
                });

                Thread serverToClient = new Thread(() -> {
                    try {
                        forwardData(serverSocket.getInputStream(), entryOutput);
                    } catch (IOException e) {
                        System.err.println("Worker " + workerId + " error in serverToClient: " + e.getMessage());
                    }
                });

                clientToServer.start();
                serverToClient.start();
                clientToServer.join();
                serverToClient.join();

                System.out.println("Worker " + workerId + " finished forwarding data for target: " + target);
            } catch (Exception e) {
                System.err.println("Worker " + workerId + " failed to connect directly to target: " + e.getMessage());
            }
        }

        /**
         * Forwards data between input and output streams.
         *
         * @param input  The input stream to read data from.
         * @param output The output stream to write data to.
         * @throws IOException If an I/O error occurs.
         */
        private void forwardData(InputStream input, OutputStream output) throws IOException {
            byte[] buffer = new byte[32768]; // Larger buffer for better throughput
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        }
    }
}
