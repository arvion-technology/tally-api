package com.arvion.tally.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small JSON codec: enough to read request bodies and write
 * responses without a dependency. Objects become LinkedHashMap, arrays become
 * List, integral numbers become Long and decimals become Double.
 */
public final class Json {
    private Json() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Boolean flag) {
            out.append(flag);
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) {
                throw new JsonException("Cannot serialize a non-finite number");
            }
            out.append(number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                writeValue(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(item, out);
            }
            out.append(']');
        } else {
            throw new JsonException("Unsupported JSON value: " + value.getClass().getName());
        }
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("JSON body is empty");
        }
        Parser parser = new Parser(text);
        Object value = parser.parseValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Unexpected trailing content at character " + parser.position());
        }
        return value;
    }

    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        throw new JsonException("Expected a JSON object");
    }

    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        throw new JsonException("Expected a JSON array");
    }

    public static String stringField(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new JsonException("Field \"" + name + "\" must be a string");
    }

    public static Long longField(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Long number) {
            return number;
        }
        if (value instanceof Integer number) {
            return number.longValue();
        }
        if (value instanceof Double number && number == Math.rint(number) && Math.abs(number) < 1e15) {
            return number.longValue();
        }
        throw new JsonException("Field \"" + name + "\" must be an integer");
    }

    public static boolean has(Map<String, Object> object, String name) {
        return object.containsKey(name);
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 64;
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        int position() {
            return index;
        }

        void skipWhitespace() {
            while (index < text.length()) {
                char ch = text.charAt(index);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                    index++;
                } else {
                    break;
                }
            }
        }

        Object parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new JsonException("JSON is nested too deeply");
            }
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("Unexpected end of JSON");
            }
            char ch = text.charAt(index);
            return switch (ch) {
                case '{' -> parseObject(depth);
                case '[' -> parseArray(depth);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new JsonException("Invalid literal at character " + index);
            }
            index += literal.length();
            return value;
        }

        private Map<String, Object> parseObject(int depth) {
            Map<String, Object> object = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new JsonException("Expected an object key at character " + index);
                }
                String key = parseString();
                skipWhitespace();
                if (!peek(':')) {
                    throw new JsonException("Expected ':' after object key at character " + index);
                }
                index++;
                object.put(key, parseValue(depth + 1));
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                if (!peek(',')) {
                    throw new JsonException("Expected ',' or '}' at character " + index);
                }
                index++;
            }
        }

        private List<Object> parseArray(int depth) {
            List<Object> list = new ArrayList<>();
            index++;
            skipWhitespace();
            if (peek(']')) {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue(depth + 1));
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return list;
                }
                if (!peek(',')) {
                    throw new JsonException("Expected ',' or ']' at character " + index);
                }
                index++;
            }
        }

        private String parseString() {
            index++;
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char ch = text.charAt(index++);
                if (ch == '"') {
                    return out.toString();
                }
                if (ch == '\\') {
                    if (index >= text.length()) {
                        throw new JsonException("Unterminated escape sequence");
                    }
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (index + 4 > text.length()) {
                                throw new JsonException("Incomplete unicode escape");
                            }
                            String hex = text.substring(index, index + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException error) {
                                throw new JsonException("Invalid unicode escape \\u" + hex);
                            }
                            index += 4;
                        }
                        default -> throw new JsonException("Invalid escape \\" + escape);
                    }
                    continue;
                }
                if (ch < 0x20) {
                    throw new JsonException("Control character in string at character " + (index - 1));
                }
                out.append(ch);
            }
            throw new JsonException("Unterminated string");
        }

        private Object parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            int digits = 0;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
                digits++;
            }
            if (digits == 0) {
                throw new JsonException("Invalid number at character " + start);
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                int fraction = 0;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                    fraction++;
                }
                if (fraction == 0) {
                    throw new JsonException("Invalid number at character " + start);
                }
            }
            if (peek('e') || peek('E')) {
                decimal = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                int exponent = 0;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                    exponent++;
                }
                if (exponent == 0) {
                    throw new JsonException("Invalid number at character " + start);
                }
            }
            String raw = text.substring(start, index);
            try {
                if (!decimal) {
                    return Long.parseLong(raw);
                }
                return Double.parseDouble(raw);
            } catch (NumberFormatException error) {
                throw new JsonException("Number out of range at character " + start);
            }
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }
    }
}
