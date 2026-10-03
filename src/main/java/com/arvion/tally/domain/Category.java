package com.arvion.tally.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Expense categories. The wire value is the lowercase name used in JSON. */
public enum Category {
    TRAVEL("travel"),
    MEALS("meals"),
    SOFTWARE("software"),
    OFFICE("office"),
    TRAINING("training"),
    OTHER("other");

    private final String wire;

    Category(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<Category> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String needle = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(category -> category.wire.equals(needle)).findFirst();
    }

    public static List<String> wires() {
        return Arrays.stream(values()).map(Category::wire).toList();
    }
}
