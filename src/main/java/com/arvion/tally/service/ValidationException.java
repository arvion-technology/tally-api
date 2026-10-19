package com.arvion.tally.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One or more field errors. The HTTP layer exposes them under "fields". */
public class ValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public ValidationException(Map<String, String> fields) {
        super(fields.values().stream().findFirst().orElse("Invalid request"));
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public ValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> fields() {
        return fields;
    }
}
