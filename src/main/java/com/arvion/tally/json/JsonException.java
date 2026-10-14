package com.arvion.tally.json;

/** Thrown when JSON cannot be parsed or written. The HTTP layer maps it to 400. */
public class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }
}
