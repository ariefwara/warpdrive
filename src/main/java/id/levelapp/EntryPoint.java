package id.levelapp;

import java.io.*;
import java.net.*;

public class EntryPoint {

    private static final int PROXY_PORT = 8010;
    private static final int FORWARD_PORT = 1080;

    public static void main(String[] args) {
        System.out.println("Starting HTTP Proxy on port " + PROXY_PORT);
        try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ProxyHandler(clientSocket)).start(); // Handle each request in a separate thread
            }
        } catch (IOException e) {
            System.err.println("Error starting proxy: " + e.getMessage());
        }
    }

    static class ProxyHandler implements Runnable {
        private final Socket clientSocket;

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
                if (parts.length < 3) return;

                if ("CONNECT".equalsIgnoreCase(parts[0])) handleConnect(parts[1], clientInput, clientOutput);
                else handleHttpRequest(parts, reader, clientOutput);

            } catch (IOException e) {
                System.err.println("Error handling request: " + e.getMessage());
            } finally {
                try { clientSocket.close(); } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleConnect(String url, InputStream clientInput, OutputStream clientOutput) {
            String[] hostPort = url.split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
        
            try (Socket serverSocket = new Socket(host, port)) {
                // Notify the client that the connection has been established
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                clientOutput.flush();
        
                // Forward data between client and server
                Thread clientToServer = new Thread(() -> {
                    try {
                        forwardData(clientInput, serverSocket.getOutputStream());
                    } catch (IOException e) {
                        System.err.println("Error in clientToServer thread: " + e.getMessage());
                    }
                });
                
                Thread serverToClient = new Thread(() -> {
                    try {
                        forwardData(serverSocket.getInputStream(), clientOutput);
                    } catch (IOException e) {
                        System.err.println("Error in serverToClient thread: " + e.getMessage());
                    }
                });
        
                clientToServer.start();
                serverToClient.start();
        
                clientToServer.join();
                serverToClient.join();
            } catch (Exception e) {
                System.err.println("Error handling CONNECT request: " + e.getMessage());
            }
        }

        private void forwardData(InputStream input, OutputStream output) {
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    output.flush();
                }
            } catch (IOException e) {
                System.err.println("Data forwarding error: " + e.getMessage());
            }
        }
        
        

        private void handleHttpRequest(String[] parts, BufferedReader reader, OutputStream clientOutput) throws IOException {
            String method = parts[0];
            String urlString = parts[1];
            String version = parts[2];

            @SuppressWarnings("deprecation")
            URL url = new URL(urlString);
            String host = url.getHost();
            int port = (url.getPort() == -1) ? 80 : url.getPort();

            try (Socket serverSocket = new Socket(host, port);
                OutputStream serverOutput = serverSocket.getOutputStream();
                InputStream serverInput = serverSocket.getInputStream()) {

                PrintWriter serverRequest = new PrintWriter(serverOutput, true);
                serverRequest.println(method + " " + url.getFile() + " " + version);
                String headerLine;
                while (!(headerLine = reader.readLine()).isEmpty()) {
                    serverRequest.println(headerLine);
                }
                serverRequest.println();

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = serverInput.read(buffer)) != -1) {
                    clientOutput.write(buffer, 0, bytesRead);
                    clientOutput.flush();
                }
            }
        }

    }
}
