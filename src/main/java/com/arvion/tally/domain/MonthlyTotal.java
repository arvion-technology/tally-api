package com.arvion.tally.domain;

/** Approved spend for one calendar month. */
public record MonthlyTotal(String month, int count, long total) {
}
