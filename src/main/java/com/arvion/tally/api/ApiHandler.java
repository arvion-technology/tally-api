package com.arvion.tally.api;

import com.arvion.tally.json.Json;
import com.arvion.tally.json.JsonException;
import com.arvion.tally.service.ConflictException;
import com.arvion.tally.service.ExpenseService;
import com.arvion.tally.service.NotFoundException;
import com.arvion.tally.service.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared HTTP plumbing: routing helpers, JSON responses and the mapping from
 * domain exceptions to status codes.
 */
public abstract class ApiHandler implements HttpHandler {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    protected final ExpenseService service;

    protected ApiHandler(ExpenseService service) {
        this.service = service;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ValidationException error) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("fields", error.fields());
            sendError(exchange, 400, "validation_failed", error.getMessage(), extra);
        } catch (NotFoundException error) {
            sendError(exchange, 404, "not_found", error.getMessage(), Map.of());
        } catch (ConflictException error) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("currentStatus", error.currentStatus());
            sendError(exchange, 409, "invalid_transition", error.getMessage(), extra);
        } catch (JsonException error) {
            sendError(exchange, 400, "invalid_json", error.getMessage(), Map.of());
        } catch (ApiException error) {
            sendError(exchange, error.status(), error.code(), error.getMessage(), Map.of());
        } catch (Exception error) {
            error.printStackTrace();
            sendError(exchange, 500, "internal_error", "Unexpected server error", Map.of());
        } finally {
            exchange.close();
        }
    }

    protected abstract void route(HttpExchange exchange) throws IOException;

    protected static void requireMethod(HttpExchange exchange, String... allowed) {
        String method = exchange.getRequestMethod();
        for (String candidate : allowed) {
            if (candidate.equals(method)) {
                return;
            }
        }
        exchange.getResponseHeaders().set("Allow", String.join(", ", allowed));
        throw new ApiException(405, "method_not_allowed", "Use " + String.join(" or ", allowed) + " for this resource");
    }

    protected static void requireGet(HttpExchange exchange) {
        requireMethod(exchange, "GET");
    }

    protected static void requirePost(HttpExchange exchange) {
        requireMethod(exchange, "POST");
    }

    protected static String[] pathSegments(HttpExchange exchange) {
        String[] raw = exchange.getRequestURI().getPath().split("/");
        List<String> segments = new ArrayList<>();
        for (String part : raw) {
            if (!part.isEmpty()) {
                segments.add(URLDecoder.decode(part, StandardCharsets.UTF_8));
            }
        }
        return segments.toArray(new String[0]);
    }

    protected static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (key.equals(name)) {
                String value = equals < 0 ? "" : pair.substring(equals + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    protected static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "payload_too_large", "Request body must be at most 64 KB");
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            throw new ApiException(400, "invalid_json", "A JSON request body is required");
        }
        return Json.object(Json.parse(text));
    }

    protected static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    protected static void sendError(
            HttpExchange exchange,
            int status,
            String code,
            String message,
            Map<String, Object> extra) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.putAll(extra);
        sendJson(exchange, status, body);
    }
}
