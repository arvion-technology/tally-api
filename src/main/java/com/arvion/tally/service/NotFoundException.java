package com.arvion.tally.service;

/** Raised when an expense id does not exist. The HTTP layer maps it to 404. */
public class NotFoundException extends RuntimeException {
    private final String id;

    public NotFoundException(String id) {
        super("No expense with id " + id);
        this.id = id;
    }

    public String id() {
        return id;
    }
}
