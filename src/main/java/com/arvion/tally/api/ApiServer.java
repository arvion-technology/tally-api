package com.arvion.tally.api;

import com.arvion.tally.service.ExpenseService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds the JDK HTTP server: three contexts plus a JSON fallback for unknown
 * paths. The server binds to loopback by default so a local demo does not
 * expose an unauthenticated API to the network.
 */
public final class ApiServer implements AutoCloseable {
    private final ExpenseService service;
    private final String host;
    private final int requestedPort;
    private HttpServer server;
    private ExecutorService executor;

    public ApiServer(ExpenseService service, int port) {
        this(service, "127.0.0.1", port);
    }

    public ApiServer(ExpenseService service, String host, int port) {
        this.service = service;
        this.host = host;
        this.requestedPort = port;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("Server is already running");
        }
        HttpServer created = HttpServer.create(new InetSocketAddress(host, requestedPort), 0);
        created.createContext("/health", new HealthHandler());
        created.createContext("/expenses", new ExpensesHandler(service));
        created.createContext("/reports", new ReportsHandler(service));
        created.createContext("/", new NotFoundHandler());
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "tally-http");
            thread.setDaemon(false);
            return thread;
        });
        created.setExecutor(executor);
        created.start();
        server = created;
    }

    public synchronized int port() {
        if (server == null) {
            throw new IllegalStateException("Server is not running");
        }
        return server.getAddress().getPort();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public void close() {
        stop();
    }
}
