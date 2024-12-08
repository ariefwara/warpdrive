package id.levelapp;

import java.io.*;
import java.net.*;

public class ExitPoint {
    private static final String ENTRY_HOST = "host.sregg.id"; // Replace with EntryPoint's IP
    private static final int ENTRY_PORT = 443;
    private static final int CONNECTION_COUNT = 10; // Number of concurrent connections to EntryPoint

    public static void main(String[] args) {
        System.out.println("Starting ExitPoint with " + CONNECTION_COUNT + " connections...");

        for (int i = 0; i < CONNECTION_COUNT; i++) {
            new Thread(new ExitPointWorker(ENTRY_HOST, ENTRY_PORT, i + 1)).start();
        }
    }

    static class ExitPointWorker implements Runnable {
        private final String entryHost;
        private final int entryPort;
        private final int workerId;

        public ExitPointWorker(String entryHost, int entryPort, int workerId) {
            this.entryHost = entryHost;
            this.entryPort = entryPort;
            this.workerId = workerId;
        }

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
