package com.arvion.tally.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One expense claim. Instances are immutable: a decision returns a new record,
 * which keeps the repository history and the test assertions easy to reason
 * about.
 */
public record Expense(
        String id,
        String employee,
        String description,
        Category category,
        long amount,
        LocalDate date,
        ExpenseStatus status,
        Instant submittedAt,
        Instant decidedAt,
        String decisionNote) {

    public Expense {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public Expense decide(ExpenseStatus next, Instant at, String note) {
        return new Expense(id, employee, description, category, amount, date, next, submittedAt, at, note);
    }
}
