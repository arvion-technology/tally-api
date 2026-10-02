package com.arvion.tally.api;

import com.arvion.tally.json.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON 404 for paths outside the documented routes. */
public final class NotFoundHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "not_found");
        body.put("message", "No route for " + exchange.getRequestURI().getPath());
        byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(404, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        } finally {
            exchange.close();
        }
    }
}
