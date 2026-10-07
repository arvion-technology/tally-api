package com.arvion.tally.domain;

import java.time.LocalDate;

/**
 * A request to create an expense. Fields are nullable here because the JSON
 * body is untrusted; the service turns missing values into field errors rather
 * than throwing while parsing.
 */
public record ExpenseRequest(
        String employee,
        String description,
        Category category,
        Long amount,
        LocalDate date) {
}
