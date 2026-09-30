package com.arvion.tally;

import com.arvion.tally.api.ApiServer;
import com.arvion.tally.repo.InMemoryExpenseRepository;
import com.arvion.tally.service.ExpenseService;
import java.io.IOException;
import java.time.Clock;

/**
 * Entry point. No framework, no configuration files:
 *
 * <pre>java -cp build/classes com.arvion.tally.Main --port 8080</pre>
 */
public final class Main {
    private static final int DEFAULT_PORT = 8080;

    private Main() {
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String host = "127.0.0.1";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port", "-p" -> {
                    if (i + 1 >= args.length) {
                        fail("--port needs a number");
                    }
                    try {
                        port = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException error) {
                        fail("--port needs a number");
                    }
                }
                case "--host" -> {
                    if (i + 1 >= args.length) {
                        fail("--host needs a value");
                    }
                    host = args[++i];
                }
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> fail("Unknown argument: " + args[i]);
            }
        }
        if (port < 0 || port > 65535) {
            fail("Port must be between 0 and 65535");
        }

        ExpenseService service = new ExpenseService(new InMemoryExpenseRepository(), Clock.systemUTC());
        ApiServer server = new ApiServer(service, host, port);
        try {
            server.start();
        } catch (IOException error) {
            System.err.println("Could not start the server: " + error.getMessage());
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "tally-shutdown"));
        System.out.println("Tally API listening on http://" + host + ":" + server.port());
        System.out.println();
        System.out.println("  GET  /health");
        System.out.println("  GET  /expenses?status=submitted&category=travel&employee=asha&month=2026-10");
        System.out.println("  POST /expenses");
        System.out.println("  GET  /expenses/{id}");
        System.out.println("  POST /expenses/{id}/status");
        System.out.println("  GET  /reports/monthly?month=2026-10");
        System.out.println("  GET  /reports/categories");
        System.out.println("  GET  /reports/summary");
        System.out.println();
        System.out.println("Press Ctrl+C to stop. Data is in memory and resets on restart.");
    }

    private static void printUsage() {
        System.out.println("Tally API on the JDK HTTP server");
        System.out.println("Usage: java com.arvion.tally.Main [--port 8080] [--host 127.0.0.1]");
        System.out.println();
        System.out.println("Compile and run with:");
        System.out.println("  bash scripts/run.sh --port 8080");
    }

    private static void fail(String message) {
        System.err.println(message);
        printUsage();
        System.exit(2);
    }
}
