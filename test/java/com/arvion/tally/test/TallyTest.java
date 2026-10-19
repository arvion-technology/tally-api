package com.arvion.tally.test;

import com.arvion.tally.api.ApiServer;
import com.arvion.tally.api.JsonMapper;
import com.arvion.tally.domain.Category;
import com.arvion.tally.domain.CategoryTotal;
import com.arvion.tally.domain.Expense;
import com.arvion.tally.domain.ExpenseFilter;
import com.arvion.tally.domain.ExpenseRequest;
import com.arvion.tally.domain.ExpenseStatus;
import com.arvion.tally.domain.MonthlyTotal;
import com.arvion.tally.domain.Summary;
import com.arvion.tally.json.Json;
import com.arvion.tally.json.JsonException;
import com.arvion.tally.repo.InMemoryExpenseRepository;
import com.arvion.tally.service.ConflictException;
import com.arvion.tally.service.ExpenseService;
import com.arvion.tally.service.NotFoundException;
import com.arvion.tally.service.ValidationException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The project's whole test suite: a small assertion harness, no JUnit.
 * Run it with scripts/test.sh.
 */
public final class TallyTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    private TallyTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Tally test harness");
        System.out.println("==================");
        jsonTests();
        validationTests();
        serviceTests();
        reportTests();
        httpTests();
        System.out.println();
        System.out.printf("Tally harness: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            failures.forEach(name -> System.out.println("  - " + name));
            System.exit(1);
        }
    }

    /* ---------------------------------------------------------------- JSON -- */

    private static void jsonTests() {
        test("json writes objects, arrays and escaped strings", () -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("text", "line\n\"quoted\"\tand slash \\");
            value.put("amount", 850L);
            value.put("ok", true);
            value.put("nothing", null);
            value.put("items", List.of(1L, "two", false));
            String json = Json.write(value);
            assertTrue(json.startsWith("{"), "object starts with a brace");
            assertTrue(json.contains("\\n"), "newline is escaped");
            assertTrue(json.contains("\\\"quoted\\\""), "quotes are escaped");
            assertTrue(json.contains("\"nothing\":null"), "null is written");
            assertTrue(json.contains("[1,\"two\",false]"), "array values are written");
        });

        test("json parses nested objects, arrays and decimals", () -> {
            Object parsed = Json.parse("{\"a\":{\"b\":[1,2,{\"c\":true}]},\"d\":2.5,\"e\":null}");
            Map<String, Object> object = Json.object(parsed);
            Map<String, Object> a = Json.object(object.get("a"));
            List<Object> b = Json.array(a.get("b"));
            assertEquals(3, b.size(), "array size");
            assertEquals(1L, b.get(0), "integer values become Long");
            assertEquals(true, Json.object(b.get(2)).get("c"), "nested boolean");
            assertEquals(2.5, object.get("d"), "decimal becomes Double");
            assertTrue(object.containsKey("e") && object.get("e") == null, "null survives parsing");
        });

        test("json rejects malformed input", () -> {
            assertThrows(JsonException.class, () -> Json.parse("{\"a\":}"), "missing value");
            assertThrows(JsonException.class, () -> Json.parse("[1,2"), "unterminated array");
            assertThrows(JsonException.class, () -> Json.parse("nul"), "bad literal");
            assertThrows(JsonException.class, () -> Json.parse("{} trailing"), "trailing content");
            assertThrows(JsonException.class, () -> Json.parse("\"unterminated"), "unterminated string");
            assertThrows(JsonException.class, () -> Json.parse(""), "empty input");
        });

        test("json round trips the values the API uses", () -> {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("id", "EXP-1001");
            original.put("amount", 1250L);
            original.put("approved", true);
            original.put("note", null);
            original.put("tags", List.of("travel", "taxi"));
            Map<String, Object> decoded = Json.object(Json.parse(Json.write(original)));
            assertEquals(original, decoded, "round trip equality");
        });

        test("json field helpers enforce types", () -> {
            Map<String, Object> object = Json.object(Json.parse("{\"name\":\"Asha\",\"amount\":42,\"other\":\"x\"}"));
            assertEquals("Asha", Json.stringField(object, "name"), "string field");
            assertEquals(42L, Json.longField(object, "amount"), "long field");
            assertTrue(Json.has(object, "name"), "has field");
            assertThrows(JsonException.class, () -> Json.stringField(object, "amount"), "string field rejects number");
            assertThrows(JsonException.class, () -> Json.longField(object, "name"), "long field rejects string");
        });
    }

    /* ---------------------------------------------------------- validation -- */

    private static void validationTests() {
        test("validation reports every missing create field", () -> {
            ExpenseService service = freshService();
            ValidationException error = assertThrows(ValidationException.class,
                    () -> service.submit(new ExpenseRequest(null, null, null, null, null)),
                    "empty request should fail");
            assertTrue(error.fields().keySet().containsAll(List.of("employee", "description", "category", "amount", "date")),
                    "expected field errors for all required fields, got " + error.fields());
        });

        test("validation enforces amount bounds", () -> {
            ExpenseService service = freshService();
            assertThrows(ValidationException.class,
                    () -> service.submit(request("Asha Shrestha", "Taxi to meeting", Category.TRAVEL, 0L, TODAY)),
                    "zero amount is invalid");
            assertThrows(ValidationException.class,
                    () -> service.submit(request("Asha Shrestha", "Taxi to meeting", Category.TRAVEL, 1_000_001L, TODAY)),
                    "amount above the cap is invalid");
            Expense smallest = service.submit(request("Asha Shrestha", "Tea at the shop", Category.MEALS, 1L, TODAY));
            assertEquals(1L, smallest.amount(), "one rupee is accepted");
        });

        test("validation rejects a future date and short fields", () -> {
            ExpenseService service = freshService();
            ValidationException future = assertThrows(ValidationException.class,
                    () -> service.submit(request("Asha Shrestha", "Taxi to meeting", Category.TRAVEL, 500L, TODAY.plusDays(1))),
                    "future date is invalid");
            assertTrue(future.fields().containsKey("date"), "date field error");

            ValidationException shortText = assertThrows(ValidationException.class,
                    () -> service.submit(request("A", "x", Category.TRAVEL, 500L, TODAY)),
                    "short employee and description are invalid");
            assertTrue(shortText.fields().containsKey("employee"), "employee error");
            assertTrue(shortText.fields().containsKey("description"), "description error");
        });
    }

    /* ------------------------------------------------------------ service -- */

    private static void serviceTests() {
        test("submit stores a submitted expense with an id and timestamps", () -> {
            ExpenseService service = freshService();
            Expense expense = service.submit(request("  Asha Shrestha  ", "  Taxi to client meeting  ", Category.TRAVEL, 850L, TODAY.minusDays(2)));
            assertEquals("EXP-1001", expense.id(), "first id");
            assertEquals(ExpenseStatus.SUBMITTED, expense.status(), "starts submitted");
            assertEquals("Asha Shrestha", expense.employee(), "employee is trimmed");
            assertEquals("Taxi to client meeting", expense.description(), "description is trimmed");
            assertEquals(NOW, expense.submittedAt(), "submitted timestamp uses the clock");
            assertEquals(null, expense.decidedAt(), "no decision yet");
            assertEquals(null, expense.decisionNote(), "no note yet");
            assertEquals(expense, service.get(expense.id()), "repository holds it");
        });

        test("submit increments the id sequence", () -> {
            ExpenseService service = freshService();
            Expense first = service.submit(request("Asha Shrestha", "First expense", Category.TRAVEL, 100L, TODAY));
            Expense second = service.submit(request("Bikash Tamang", "Second expense", Category.MEALS, 200L, TODAY));
            assertEquals("EXP-1001", first.id(), "first id");
            assertEquals("EXP-1002", second.id(), "second id");
        });

        test("approve moves submitted to approved with a decision time", () -> {
            ExpenseService service = freshService();
            Expense submitted = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, TODAY));
            Expense approved = service.decide(submitted.id(), ExpenseStatus.APPROVED, "  Approved for travel  ");
            assertEquals(ExpenseStatus.APPROVED, approved.status(), "status approved");
            assertEquals(NOW, approved.decidedAt(), "decision time");
            assertEquals("Approved for travel", approved.decisionNote(), "note is trimmed");
            assertEquals(ExpenseStatus.APPROVED, service.get(submitted.id()).status(), "repository updated");
        });

        test("a rejection needs a readable note", () -> {
            ExpenseService service = freshService();
            Expense expense = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, TODAY));
            ValidationException blank = assertThrows(ValidationException.class,
                    () -> service.decide(expense.id(), ExpenseStatus.REJECTED, "   "), "blank note");
            assertTrue(blank.fields().containsKey("note"), "note field error");
            assertThrows(ValidationException.class,
                    () -> service.decide(expense.id(), ExpenseStatus.REJECTED, "no"), "short note");

            Expense rejected = service.decide(expense.id(), ExpenseStatus.REJECTED, "Receipt missing");
            assertEquals(ExpenseStatus.REJECTED, rejected.status(), "status rejected");
            assertEquals("Receipt missing", rejected.decisionNote(), "note stored");
        });

        test("decided expenses cannot be decided again", () -> {
            ExpenseService service = freshService();
            Expense expense = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, TODAY));
            service.decide(expense.id(), ExpenseStatus.APPROVED, "Looks right");
            ConflictException error = assertThrows(ConflictException.class,
                    () -> service.decide(expense.id(), ExpenseStatus.REJECTED, "Too late"), "second decision");
            assertEquals("approved", error.currentStatus(), "current status in the error");
        });

        test("only decisions can be applied and ids must exist", () -> {
            ExpenseService service = freshService();
            Expense expense = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, TODAY));
            assertThrows(ValidationException.class,
                    () -> service.decide(expense.id(), ExpenseStatus.SUBMITTED, null), "submitted is not a decision");
            assertThrows(ValidationException.class,
                    () -> service.decide(expense.id(), null, null), "null decision");
            assertThrows(NotFoundException.class, () -> service.get("EXP-9999"), "unknown id");
            assertThrows(NotFoundException.class, () -> service.decide("EXP-9999", ExpenseStatus.APPROVED, null), "unknown decision id");
        });

        test("long decision notes are refused", () -> {
            ExpenseService service = freshService();
            Expense expense = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, TODAY));
            String longNote = "x".repeat(201);
            ValidationException error = assertThrows(ValidationException.class,
                    () -> service.decide(expense.id(), ExpenseStatus.APPROVED, longNote), "long note");
            assertTrue(error.fields().containsKey("note"), "note error");
        });

        test("list filters by status, category, employee and month", () -> {
            ExpenseService service = sampleService();
            assertEquals(5, service.list(ExpenseFilter.empty()).size(), "all expenses");
            assertEquals(3, service.list(new ExpenseFilter(ExpenseStatus.APPROVED, null, null, null)).size(), "approved");
            assertEquals(1, service.list(new ExpenseFilter(null, Category.TRAVEL, null, null)).size(), "travel");
            assertEquals(3, service.list(new ExpenseFilter(null, null, "asha", null)).size(), "employee contains");
            assertEquals(3, service.list(new ExpenseFilter(null, null, null, "2026-10")).size(), "october");
            assertEquals(2, service.list(new ExpenseFilter(ExpenseStatus.APPROVED, null, null, "2026-10")).size(), "approved in october");
            assertThrows(ValidationException.class,
                    () -> service.list(new ExpenseFilter(null, null, null, "2026-13")), "invalid month");
        });
    }

    /* ------------------------------------------------------------- reports -- */

    private static void reportTests() {
        test("monthly report groups approved expenses by month, oldest first", () -> {
            ExpenseService service = sampleService();
            List<MonthlyTotal> report = service.monthly(null);
            assertEquals(2, report.size(), "two months with approved spend");
            assertEquals("2026-09", report.get(0).month(), "oldest month first");
            assertEquals(12000L, report.get(0).total(), "september software");
            assertEquals(1, report.get(0).count(), "september count");
            assertEquals("2026-10", report.get(1).month(), "october second");
            assertEquals(2100L, report.get(1).total(), "october travel and meals");
            assertEquals(2, report.get(1).count(), "october count");
        });

        test("monthly report can be narrowed to one month and rejects bad input", () -> {
            ExpenseService service = sampleService();
            List<MonthlyTotal> october = service.monthly("2026-10");
            assertEquals(1, october.size(), "one month");
            assertEquals(2100L, october.get(0).total(), "october total");
            assertThrows(ValidationException.class, () -> service.monthly("2026-13"), "invalid month");
        });

        test("category report totals approved spend and rounds percentages", () -> {
            ExpenseService service = sampleService();
            List<CategoryTotal> report = service.categories(null);
            assertEquals(3, report.size(), "three approved categories");
            assertEquals(Category.SOFTWARE, report.get(0).category(), "largest category first");
            assertEquals(12000L, report.get(0).total(), "software total");
            assertEquals(1, report.get(0).count(), "software count");
            int percentSum = report.stream().mapToInt(CategoryTotal::percent).sum();
            assertEquals(100, percentSum, "rounded percentages add to 100 here");
            List<CategoryTotal> october = service.categories("2026-10");
            assertEquals(2, october.size(), "october categories");
            assertEquals(2100L, october.stream().mapToLong(CategoryTotal::total).sum(), "october total");
        });

        test("summary counts statuses and pending value", () -> {
            ExpenseService service = sampleService();
            Summary summary = service.summary();
            assertEquals(5, summary.total(), "five expenses");
            assertEquals(1, summary.submitted(), "one submitted");
            assertEquals(3, summary.approved(), "three approved");
            assertEquals(1, summary.rejected(), "one rejected");
            assertEquals(14100L, summary.approvedTotal(), "approved total");
            assertEquals(300L, summary.pendingTotal(), "pending total");
        });

        test("json mapper serialises dates, statuses and null decisions", () -> {
            ExpenseService service = freshService();
            Expense expense = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, TODAY));
            Map<String, Object> json = JsonMapper.expense(expense);
            assertEquals("EXP-1001", json.get("id"), "id");
            assertEquals("travel", json.get("category"), "category wire value");
            assertEquals("submitted", json.get("status"), "status wire value");
            assertEquals("2026-10-28", json.get("date"), "date is an ISO string");
            assertEquals("2026-10-28T09:00:00Z", json.get("submittedAt"), "timestamp is an ISO string");
            assertTrue(json.containsKey("decidedAt") && json.get("decidedAt") == null, "decidedAt is null");
            assertTrue(json.containsKey("decisionNote") && json.get("decisionNote") == null, "decisionNote is null");
        });
    }

    /* ---------------------------------------------------------------- HTTP -- */

    private static void httpTests() throws Exception {
        ExpenseService service = freshService();
        ApiServer server = new ApiServer(service, "127.0.0.1", 0);
        server.start();
        int port = server.port();
        HttpClient client = HttpClient.newHttpClient();
        try {
            test("http health endpoint responds with JSON", () -> {
                Response response = send(client, port, "GET", "/health", null);
                assertEquals(200, response.status(), "health status");
                assertTrue(response.contentType().startsWith("application/json"), "json content type");
                Map<String, Object> body = Json.object(Json.parse(response.body()));
                assertEquals("ok", body.get("status"), "health status body");
                assertEquals("tally", body.get("service"), "service name");
            });

            String[] createdId = new String[1];

            test("http create returns 201, Location and the expense", () -> {
                String body = "{\"employee\":\"Http Tester\",\"description\":\"Taxi to the airport\","
                        + "\"category\":\"travel\",\"amount\":900,\"date\":\"2026-10-20\"}";
                Response response = send(client, port, "POST", "/expenses", body);
                assertEquals(201, response.status(), "created");
                assertEquals("/expenses/EXP-1001", response.header("location"), "location header");
                Map<String, Object> json = Json.object(Json.parse(response.body()));
                assertEquals("Http Tester", json.get("employee"), "employee");
                assertEquals(900L, json.get("amount"), "amount");
                assertEquals("submitted", json.get("status"), "status");
                createdId[0] = (String) json.get("id");

                Response fetched = send(client, port, "GET", "/expenses/" + createdId[0], null);
                assertEquals(200, fetched.status(), "get by id");
                assertEquals(createdId[0], Json.object(Json.parse(fetched.body())).get("id"), "same id");
            });

            test("http validation returns 400 with field errors", () -> {
                String body = "{\"employee\":\"\",\"description\":\"x\",\"category\":\"travel\",\"amount\":0,\"date\":\"2026-10-20\"}";
                Response response = send(client, port, "POST", "/expenses", body);
                assertEquals(400, response.status(), "bad request");
                Map<String, Object> json = Json.object(Json.parse(response.body()));
                assertEquals("validation_failed", json.get("error"), "error code");
                Map<String, Object> fields = Json.object(json.get("fields"));
                assertTrue(fields.containsKey("employee") && fields.containsKey("description") && fields.containsKey("amount"),
                        "field errors present: " + fields);
            });

            test("http malformed JSON returns 400 invalid_json", () -> {
                Response response = send(client, port, "POST", "/expenses", "{\"employee\":");
                assertEquals(400, response.status(), "bad json");
                assertEquals("invalid_json", Json.object(Json.parse(response.body())).get("error"), "error code");
            });

            test("http approve transition updates the expense", () -> {
                String body = "{\"status\":\"approved\",\"note\":\"Approved by finance\"}";
                Response response = send(client, port, "POST", "/expenses/" + createdId[0] + "/status", body);
                assertEquals(200, response.status(), "approved");
                Map<String, Object> json = Json.object(Json.parse(response.body()));
                assertEquals("approved", json.get("status"), "status approved");
                assertEquals("Approved by finance", json.get("decisionNote"), "note stored");
                assertTrue(json.get("decidedAt") != null, "decidedAt set");
            });

            test("http second decision returns 409 with current status", () -> {
                String body = "{\"status\":\"rejected\",\"note\":\"Too late\"}";
                Response response = send(client, port, "POST", "/expenses/" + createdId[0] + "/status", body);
                assertEquals(409, response.status(), "conflict");
                Map<String, Object> json = Json.object(Json.parse(response.body()));
                assertEquals("invalid_transition", json.get("error"), "error code");
                assertEquals("approved", json.get("currentStatus"), "current status");
            });

            test("http rejection without a note returns 400", () -> {
                Response created = send(client, port, "POST", "/expenses",
                        "{\"employee\":\"Reject Tester\",\"description\":\"Dinner receipt\",\"category\":\"meals\","
                                + "\"amount\":1200,\"date\":\"2026-10-21\"}");
                String id = (String) Json.object(Json.parse(created.body())).get("id");
                Response response = send(client, port, "POST", "/expenses/" + id + "/status",
                        "{\"status\":\"rejected\"}");
                assertEquals(400, response.status(), "note required");
                assertTrue(Json.object(Json.parse(response.body())).containsKey("fields"), "fields present");
            });

            test("http unknown ids and routes return JSON 404", () -> {
                assertEquals(404, send(client, port, "GET", "/expenses/EXP-9999", null).status(), "unknown expense");
                Response route = send(client, port, "GET", "/nope", null);
                assertEquals(404, route.status(), "unknown route");
                assertEquals("not_found", Json.object(Json.parse(route.body())).get("error"), "route error code");
            });

            test("http method not allowed includes Allow header", () -> {
                Response response = send(client, port, "PUT", "/expenses", "{}");
                assertEquals(405, response.status(), "method not allowed");
                assertTrue(response.header("allow") != null && response.header("allow").contains("POST"), "allow header");
            });

            test("http list filters use query parameters", () -> {
                Response response = send(client, port, "GET", "/expenses?employee=Http%20Tester", null);
                assertEquals(200, response.status(), "list status");
                Map<String, Object> json = Json.object(Json.parse(response.body()));
                assertTrue(((Number) json.get("count")).intValue() >= 1, "at least one match");
                List<Object> items = Json.array(json.get("items"));
                for (Object item : items) {
                    assertEquals("Http Tester", Json.object(item).get("employee"), "filtered employee");
                }
            });

            test("http reports return the expected shapes", () -> {
                Response monthly = send(client, port, "GET", "/reports/monthly", null);
                assertEquals(200, monthly.status(), "monthly status");
                Map<String, Object> monthlyJson = Json.object(Json.parse(monthly.body()));
                assertTrue(monthlyJson.containsKey("months"), "months array");
                assertTrue(((Number) monthlyJson.get("total")).longValue() >= 900L, "approved travel included");

                Response categories = send(client, port, "GET", "/reports/categories", null);
                assertEquals(200, categories.status(), "categories status");
                List<Object> categoryItems = Json.array(Json.object(Json.parse(categories.body())).get("categories"));
                assertTrue(categoryItems.size() >= 1, "at least one category");

                Response summary = send(client, port, "GET", "/reports/summary", null);
                assertEquals(200, summary.status(), "summary status");
                Map<String, Object> summaryJson = Json.object(Json.parse(summary.body()));
                assertTrue(((Number) summaryJson.get("total")).intValue() >= 2, "summary counts");

                Response filtered = send(client, port, "GET", "/reports/monthly?month=2026-10", null);
                assertEquals(200, filtered.status(), "filtered report status");
                assertTrue(Json.array(Json.object(Json.parse(filtered.body())).get("months")).size() == 1,
                        "one filtered month");
            });
        } finally {
            server.stop();
        }
    }

    /* ------------------------------------------------------------- helpers -- */

    private static ExpenseService freshService() {
        return new ExpenseService(new InMemoryExpenseRepository(), CLOCK);
    }

    private static ExpenseRequest request(String employee, String description, Category category, Long amount, LocalDate date) {
        return new ExpenseRequest(employee, description, category, amount, date);
    }

    private static ExpenseService sampleService() {
        ExpenseService service = freshService();
        Expense travel = service.submit(request("Asha Shrestha", "Taxi to client meeting", Category.TRAVEL, 850L, LocalDate.of(2026, 10, 24)));
        Expense meals = service.submit(request("Asha Shrestha", "Team lunch", Category.MEALS, 1250L, LocalDate.of(2026, 10, 20)));
        Expense software = service.submit(request("Bikash Tamang", "Editor licence", Category.SOFTWARE, 12000L, LocalDate.of(2026, 9, 15)));
        Expense training = service.submit(request("Sita Gurung", "Conference fee", Category.TRAINING, 4800L, LocalDate.of(2026, 10, 3)));
        Expense other = service.submit(request("Asha Shrestha", "Courier", Category.OTHER, 300L, LocalDate.of(2026, 8, 11)));
        service.decide(travel.id(), ExpenseStatus.APPROVED, "Approved for travel");
        service.decide(meals.id(), ExpenseStatus.APPROVED, "Team lunch approved");
        service.decide(software.id(), ExpenseStatus.APPROVED, "Tooling");
        service.decide(training.id(), ExpenseStatus.REJECTED, "Missing receipt");
        return service;
    }

    private record Response(int status, String body, String contentType, String location, String allow) {
        String header(String name) {
            return "location".equals(name) ? location : "allow".equals(name) ? allow : null;
        }
    }

    private static Response send(HttpClient client, int port, String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String location = response.headers().firstValue("Location").orElse(null);
        String allow = response.headers().firstValue("Allow").orElse(null);
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        return new Response(response.statusCode(), response.body(), contentType, location, allow);
    }

    /* --------------------------------------------------------- harness core -- */

    private static void test(String name, ThrowingRunnable body) {
        try {
            body.run();
            passed++;
            System.out.println("ok   " + name);
        } catch (Throwable error) {
            failed++;
            failures.add(name);
            System.out.println("FAIL " + name);
            System.out.println("     " + error);
            StackTraceElement[] trace = error.getStackTrace();
            for (int i = 0; i < Math.min(4, trace.length); i++) {
                System.out.println("     at " + trace[i]);
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static <T extends Throwable> T assertThrows(Class<T> type, ThrowingRunnable body, String message) {
        try {
            body.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return type.cast(error);
            }
            throw new AssertionError(message + " (got " + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
        throw new AssertionError(message + " (nothing was thrown)");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
