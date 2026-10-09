package com.arvion.tally.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Expense lifecycle: submitted, approved, rejected. */
public enum ExpenseStatus {
    SUBMITTED("submitted"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String wire;

    ExpenseStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public boolean isDecision() {
        return this == APPROVED || this == REJECTED;
    }

    public static Optional<ExpenseStatus> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String needle = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(status -> status.wire.equals(needle)).findFirst();
    }

    public static List<String> wires() {
        return Arrays.stream(values()).map(ExpenseStatus::wire).toList();
    }
}
