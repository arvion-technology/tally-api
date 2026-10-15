package com.arvion.tally.service;

/** Raised when a status change is not allowed. The HTTP layer maps it to 409. */
public class ConflictException extends RuntimeException {
    private final String currentStatus;

    public ConflictException(String message, String currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public String currentStatus() {
        return currentStatus;
    }
}
