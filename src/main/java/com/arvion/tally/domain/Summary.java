package com.arvion.tally.domain;

/** Counts and totals behind the opening screen of the demo. */
public record Summary(
        int total,
        int submitted,
        int approved,
        int rejected,
        long approvedTotal,
        long pendingTotal) {
}
