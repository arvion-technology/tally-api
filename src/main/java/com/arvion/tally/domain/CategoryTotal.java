package com.arvion.tally.domain;

/** Approved spend for one category, with a rounded percentage of the total. */
public record CategoryTotal(Category category, int count, long total, int percent) {
}
