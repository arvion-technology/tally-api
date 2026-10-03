package com.arvion.tally.api;

import com.arvion.tally.service.ExpenseService;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/** Read-only report routes under /reports. */
public final class ReportsHandler extends ApiHandler {
    public ReportsHandler(ExpenseService service) {
        super(service);
    }

    @Override
    protected void route(HttpExchange exchange) throws IOException {
        requireGet(exchange);
        String[] segments = pathSegments(exchange);
        if (segments.length != 2 || !segments[0].equals("reports")) {
            throw new ApiException(404, "not_found", "No route for " + exchange.getRequestURI().getPath());
        }
        String month = ExpenseService.normalizeMonth(queryParam(exchange, "month"));
        switch (segments[1]) {
            case "monthly" -> sendJson(exchange, 200, JsonMapper.monthly(service.monthly(month)));
            case "categories" -> sendJson(exchange, 200, JsonMapper.categories(service.categories(month)));
            case "summary" -> sendJson(exchange, 200, JsonMapper.summary(service.summary()));
            default -> throw new ApiException(404, "not_found", "Unknown report: " + segments[1]);
        }
    }
}
