package com.arvion.tally.service;

import com.arvion.tally.domain.Category;
import com.arvion.tally.domain.CategoryTotal;
import com.arvion.tally.domain.Expense;
import com.arvion.tally.domain.ExpenseFilter;
import com.arvion.tally.domain.ExpenseRequest;
import com.arvion.tally.domain.ExpenseStatus;
import com.arvion.tally.domain.MonthlyTotal;
import com.arvion.tally.domain.Summary;
import com.arvion.tally.repo.ExpenseRepository;
import com.arvion.tally.repo.InMemoryExpenseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business rules for submitting, deciding, filtering and reporting expenses.
 * The service owns validation and the status machine; handlers only translate
 * HTTP to these calls.
 */
public final class ExpenseService {
    public static final int MAX_AMOUNT = 1_000_000;
    public static final int MIN_NOTE = 3;
    public static final int MAX_NOTE = 200;

    private final ExpenseRepository repository;
    private final Clock clock;
    private final AtomicInteger idSequence;

    public ExpenseService(ExpenseRepository repository, Clock clock) {
        this(repository, clock, 1000);
    }

    public ExpenseService(ExpenseRepository repository, Clock clock, int firstSequence) {
        this.repository = repository;
        this.clock = clock;
        this.idSequence = new AtomicInteger(firstSequence);
    }

    public ExpenseService() {
        this(new InMemoryExpenseRepository(), Clock.systemUTC());
    }

    public Expense submit(ExpenseRequest request) {
        Map<String, String> errors = validateRequest(request, LocalDate.now(clock));
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        String id = "EXP-" + idSequence.incrementAndGet();
        Expense expense = new Expense(
                id,
                request.employee().trim(),
                request.description().trim(),
                request.category(),
                request.amount(),
                request.date(),
                ExpenseStatus.SUBMITTED,
                Instant.now(clock),
                null,
                null);
        return repository.save(expense);
    }

    public Expense get(String id) {
        if (id == null || id.isBlank()) {
            throw new NotFoundException(String.valueOf(id));
        }
        return repository.findById(id.trim())
                .orElseThrow(() -> new NotFoundException(id));
    }

    public List<Expense> list(ExpenseFilter filter) {
        ExpenseFilter applied = filter == null ? ExpenseFilter.empty() : filter;
        String month = normalizeMonth(applied.month());
        String employee = applied.employee() == null ? null : applied.employee().trim();
        List<Expense> matches = new ArrayList<>();
        for (Expense expense : repository.findAll()) {
            if (applied.status() != null && expense.status() != applied.status()) {
                continue;
            }
            if (applied.category() != null && expense.category() != applied.category()) {
                continue;
            }
            if (employee != null && !employee.isEmpty()
                    && !expense.employee().toLowerCase(Locale.ROOT).contains(employee.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (month != null && !month.equals(YearMonth.from(expense.date()).toString())) {
                continue;
            }
            matches.add(expense);
        }
        matches.sort(Comparator.comparing(Expense::submittedAt).reversed().thenComparing(Expense::id));
        return List.copyOf(matches);
    }

    public Expense decide(String id, ExpenseStatus target, String note) {
        if (target == null) {
            throw new ValidationException("status", "status is required");
        }
        if (!target.isDecision()) {
            throw new ValidationException("status", "status must be approved or rejected");
        }
        Expense current = get(id);
        if (current.status() != ExpenseStatus.SUBMITTED) {
            throw new ConflictException(
                    "Expense " + current.id() + " is already " + current.status().wire(),
                    current.status().wire());
        }
        String cleanNote = note == null ? "" : note.trim();
        Map<String, String> errors = new LinkedHashMap<>();
        if (target == ExpenseStatus.REJECTED && cleanNote.length() < MIN_NOTE) {
            errors.put("note", "A rejection needs a short reason (at least " + MIN_NOTE + " characters)");
        }
        if (cleanNote.length() > MAX_NOTE) {
            errors.put("note", "note must be at most " + MAX_NOTE + " characters");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Expense updated = current.decide(target, Instant.now(clock), cleanNote.isEmpty() ? null : cleanNote);
        return repository.save(updated);
    }

    public List<MonthlyTotal> monthly(String month) {
        String selected = normalizeMonth(month);
        Map<YearMonth, long[]> totals = new TreeMap<>();
        for (Expense expense : repository.findAll()) {
            if (expense.status() != ExpenseStatus.APPROVED) {
                continue;
            }
            YearMonth key = YearMonth.from(expense.date());
            if (selected != null && !selected.equals(key.toString())) {
                continue;
            }
            long[] bucket = totals.computeIfAbsent(key, ignored -> new long[2]);
            bucket[0] += 1;
            bucket[1] += expense.amount();
        }
        List<MonthlyTotal> report = new ArrayList<>();
        for (Map.Entry<YearMonth, long[]> entry : totals.entrySet()) {
            report.add(new MonthlyTotal(entry.getKey().toString(), (int) entry.getValue()[0], entry.getValue()[1]));
        }
        return List.copyOf(report);
    }

    public List<CategoryTotal> categories(String month) {
        String selected = normalizeMonth(month);
        Map<Category, long[]> totals = new EnumMap<>(Category.class);
        long grandTotal = 0;
        for (Expense expense : repository.findAll()) {
            if (expense.status() != ExpenseStatus.APPROVED) {
                continue;
            }
            if (selected != null && !selected.equals(YearMonth.from(expense.date()).toString())) {
                continue;
            }
            long[] bucket = totals.computeIfAbsent(expense.category(), ignored -> new long[2]);
            bucket[0] += 1;
            bucket[1] += expense.amount();
            grandTotal += expense.amount();
        }
        List<CategoryTotal> report = new ArrayList<>();
        for (Map.Entry<Category, long[]> entry : totals.entrySet()) {
            long total = entry.getValue()[1];
            int percent = grandTotal == 0 ? 0 : (int) Math.round((total * 100.0) / grandTotal);
            report.add(new CategoryTotal(entry.getKey(), (int) entry.getValue()[0], total, percent));
        }
        report.sort(Comparator.comparingLong(CategoryTotal::total).reversed()
                .thenComparing(item -> item.category().wire()));
        return List.copyOf(report);
    }

    public Summary summary() {
        int submitted = 0;
        int approved = 0;
        int rejected = 0;
        long approvedTotal = 0;
        long pendingTotal = 0;
        for (Expense expense : repository.findAll()) {
            switch (expense.status()) {
                case SUBMITTED -> {
                    submitted++;
                    pendingTotal += expense.amount();
                }
                case APPROVED -> {
                    approved++;
                    approvedTotal += expense.amount();
                }
                case REJECTED -> rejected++;
            }
        }
        return new Summary(submitted + approved + rejected, submitted, approved, rejected, approvedTotal, pendingTotal);
    }

    /** Calendar month string used by reports, normalised to YYYY-MM. */
    public static String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim()).toString();
        } catch (DateTimeParseException error) {
            throw new ValidationException("month", "month must be formatted as YYYY-MM");
        }
    }

    private static Map<String, String> validateRequest(ExpenseRequest request, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request == null) {
            errors.put("body", "A JSON body is required");
            return errors;
        }
        String employee = request.employee() == null ? "" : request.employee().trim();
        if (employee.length() < 2 || employee.length() > 80) {
            errors.put("employee", "employee must be between 2 and 80 characters");
        }
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() < 3 || description.length() > 140) {
            errors.put("description", "description must be between 3 and 140 characters");
        }
        if (request.category() == null) {
            errors.put("category", "category must be one of " + String.join(", ", Category.wires()));
        }
        if (request.amount() == null) {
            errors.put("amount", "amount is required");
        } else if (request.amount() < 1 || request.amount() > MAX_AMOUNT) {
            errors.put("amount", "amount must be between 1 and " + MAX_AMOUNT + " rupees");
        }
        if (request.date() == null) {
            errors.put("date", "date must be formatted as YYYY-MM-DD");
        } else if (request.date().isAfter(today)) {
            errors.put("date", "date cannot be in the future");
        }
        return errors;
    }
}
