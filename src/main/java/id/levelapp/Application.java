package id.levelapp;

/**
 * The Application class serves as the entry point for the Warpdrive application.
 * It operates in two modes: 'entry' and 'exit', facilitating HTTP proxy server operations
 * and request forwarding between client connections and target servers.
 */
public class Application {

    /**
     * The main method processes command-line arguments to determine the mode of operation
     * and configure the application accordingly.
     *
     * @param args Command-line arguments where:
     *             <ul>
     *             <li>args[0] - Mode of operation ('entry' or 'exit').</li>
     *             <li>For 'entry' mode:
     *                 <ul>
     *                     <li>args[1] - Proxy port number (optional, default is EntryPoint.PROXY_PORT).</li>
     *                     <li>args[2] - Exit server port number (optional, default is EntryPoint.EXIT_SERVER_PORT).</li>
     *                     <li>args[3] - Forward mode (optional, true or false, default is EntryPoint.IS_FORWARD).</li>
     *                 </ul>
     *             </li>
     *             <li>For 'exit' mode:
     *                 <ul>
     *                     <li>args[1] - Entry host (optional, default is ExitPoint.ENTRY_HOST).</li>
     *                     <li>args[2] - Entry port number (optional, default is ExitPoint.ENTRY_PORT).</li>
     *                     <li>args[3] - Number of concurrent connections (optional, default is ExitPoint.CONNECTION_COUNT).</li>
     *                 </ul>
     *             </li>
     *             </ul>
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("This application can operate in two modes: 'entry' and 'exit'.");
            System.err.println("In 'entry' mode, the application acts as an HTTP proxy server, listening for client connections and optionally forwarding requests to exit points.");
            System.err.println("In 'exit' mode, the application connects to an entry point and forwards requests to target servers.");
            System.err.println("Please specify 'entry' or 'exit' as the first argument to determine the mode of operation.");
            System.err.println("For 'entry' mode, you can optionally specify the following arguments:");
            System.err.println("  - Second argument: Proxy port number (default is " + EntryPoint.PROXY_PORT + ").");
            System.err.println("  - Third argument: Exit server port number (default is " + EntryPoint.EXIT_SERVER_PORT + ").");
            System.err.println("  - Fourth argument: Forward mode (true or false, default is " + EntryPoint.IS_FORWARD + ").");
            System.err.println("For 'exit' mode, you can optionally specify the following arguments:");
            System.err.println("  - Second argument: Entry host (default is " + ExitPoint.ENTRY_HOST + ").");
            System.err.println("  - Third argument: Entry port number (default is " + ExitPoint.ENTRY_PORT + ").");
            System.err.println("  - Fourth argument: Number of concurrent connections (default is " + ExitPoint.CONNECTION_COUNT + ").");
            return;
        }

        String mode = args[0].toLowerCase();
        switch (mode) {
            case "entry":
                EntryPoint.PROXY_PORT = args.length > 1 ? Integer.parseInt(args[1]) : EntryPoint.PROXY_PORT;
                EntryPoint.EXIT_SERVER_PORT = args.length > 2 ? Integer.parseInt(args[2]) : EntryPoint.EXIT_SERVER_PORT;
                EntryPoint.IS_FORWARD = args.length > 3 ? Boolean.parseBoolean(args[3]) : EntryPoint.IS_FORWARD;
                EntryPoint.start(args);
                break;

            case "exit":
                ExitPoint.ENTRY_HOST = args.length > 1 ? args[1] : ExitPoint.ENTRY_HOST;
                ExitPoint.ENTRY_PORT = args.length > 2 ? Integer.parseInt(args[2]) : ExitPoint.ENTRY_PORT;
                ExitPoint.CONNECTION_COUNT = args.length > 3 ? Integer.parseInt(args[3]) : ExitPoint.CONNECTION_COUNT;
                ExitPoint.start(args);
                break;

            default:
                System.err.println("Invalid mode. Please specify 'entry' or 'exit'.");
        }
    }
}
