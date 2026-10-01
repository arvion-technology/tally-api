package com.arvion.tally.api;

import com.arvion.tally.domain.Category;
import com.arvion.tally.domain.Expense;
import com.arvion.tally.domain.ExpenseFilter;
import com.arvion.tally.domain.ExpenseRequest;
import com.arvion.tally.domain.ExpenseStatus;
import com.arvion.tally.json.Json;
import com.arvion.tally.json.JsonException;
import com.arvion.tally.service.ExpenseService;
import com.arvion.tally.service.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Routes /expenses, /expenses/{id} and /expenses/{id}/status. */
public final class ExpensesHandler extends ApiHandler {
    public ExpensesHandler(ExpenseService service) {
        super(service);
    }

    @Override
    protected void route(HttpExchange exchange) throws IOException {
        String[] segments = pathSegments(exchange);
        if (segments.length == 1 && segments[0].equals("expenses")) {
            if ("POST".equals(exchange.getRequestMethod())) {
                create(exchange);
            } else if ("GET".equals(exchange.getRequestMethod())) {
                list(exchange);
            } else {
                requireMethod(exchange, "GET", "POST");
            }
            return;
        }
        if (segments.length == 2 && segments[0].equals("expenses")) {
            requireGet(exchange);
            Expense expense = service.get(segments[1]);
            sendJson(exchange, 200, JsonMapper.expense(expense));
            return;
        }
        if (segments.length == 3 && segments[0].equals("expenses") && segments[2].equals("status")) {
            requirePost(exchange);
            decide(exchange, segments[1]);
            return;
        }
        throw new ApiException(404, "not_found", "No route for " + exchange.getRequestURI().getPath());
    }

    private void create(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        ExpenseRequest request = parseRequest(body);
        Expense created = service.submit(request);
        exchange.getResponseHeaders().set("Location", "/expenses/" + created.id());
        sendJson(exchange, 201, JsonMapper.expense(created));
    }

    private void list(HttpExchange exchange) throws IOException {
        ExpenseStatus status = null;
        String rawStatus = queryParam(exchange, "status");
        if (rawStatus != null && !rawStatus.isBlank()) {
            status = ExpenseStatus.fromWire(rawStatus)
                    .orElseThrow(() -> new ValidationException("status",
                            "status must be one of " + String.join(", ", ExpenseStatus.wires())));
        }
        Category category = null;
        String rawCategory = queryParam(exchange, "category");
        if (rawCategory != null && !rawCategory.isBlank()) {
            category = Category.fromWire(rawCategory)
                    .orElseThrow(() -> new ValidationException("category",
                            "category must be one of " + String.join(", ", Category.wires())));
        }
        String employee = queryParam(exchange, "employee");
        String month = ExpenseService.normalizeMonth(queryParam(exchange, "month"));
        ExpenseFilter filter = new ExpenseFilter(status, category, employee, month);
        sendJson(exchange, 200, JsonMapper.expenseList(service.list(filter)));
    }

    private void decide(HttpExchange exchange, String id) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        String rawStatus = Json.stringField(body, "status");
        ExpenseStatus status = rawStatus == null
                ? null
                : ExpenseStatus.fromWire(rawStatus)
                        .orElseThrow(() -> new ValidationException("status",
                                "status must be one of " + String.join(", ", ExpenseStatus.wires())));
        String note = Json.stringField(body, "note");
        Expense updated = service.decide(id, status, note);
        sendJson(exchange, 200, JsonMapper.expense(updated));
    }

    private static ExpenseRequest parseRequest(Map<String, Object> body) {
        String employee = Json.stringField(body, "employee");
        String description = Json.stringField(body, "description");
        String rawCategory = Json.stringField(body, "category");
        String rawDate = Json.stringField(body, "date");

        Category category = null;
        if (rawCategory != null && !rawCategory.isBlank()) {
            category = Category.fromWire(rawCategory)
                    .orElseThrow(() -> new ValidationException("category",
                            "category must be one of " + String.join(", ", Category.wires())));
        }

        LocalDate date = null;
        if (rawDate != null && !rawDate.isBlank()) {
            try {
                date = LocalDate.parse(rawDate.trim());
            } catch (DateTimeParseException error) {
                throw new ValidationException("date", "date must be formatted as YYYY-MM-DD");
            }
        }

        Long amount = null;
        try {
            amount = Json.longField(body, "amount");
        } catch (JsonException error) {
            throw new ValidationException("amount", "amount must be an integer number of rupees");
        }
        return new ExpenseRequest(employee, description, category, amount, date);
    }
}
