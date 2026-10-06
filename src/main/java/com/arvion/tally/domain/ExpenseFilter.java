package com.arvion.tally.domain;

/** Optional filters for the expense list endpoint. All fields may be null. */
public record ExpenseFilter(
        ExpenseStatus status,
        Category category,
        String employee,
        String month) {

    public static ExpenseFilter empty() {
        return new ExpenseFilter(null, null, null, null);
    }
}
