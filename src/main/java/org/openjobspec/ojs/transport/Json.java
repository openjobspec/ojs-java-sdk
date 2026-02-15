package org.openjobspec.ojs.transport;

import java.util.*;

/**
 * Minimal JSON encoder/decoder using only the standard library.
 * Handles maps, lists, strings, numbers, booleans, and null.
 *
 * <p>This is an internal utility class for the OJS SDK transport layer.
 * It provides zero-dependency JSON handling suitable for the OJS wire format.
 */
public final class Json {

    /** Maximum nesting depth for JSON parsing (default: 128). */
    static final int MAX_DEPTH = 128;

    /** Maximum input length for JSON parsing (default: 10 MB). */
    static final int MAX_INPUT_LENGTH = 10 * 1024 * 1024;

    private Json() {}

    /** Encode an object to a JSON string. */
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return encodeString(s);
        if (value instanceof Number n) return n.toString();
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Map<?, ?> map) return encodeMap(map);
        if (value instanceof List<?> list) return encodeList(list);
        if (value instanceof Object[] arr) return encodeList(Arrays.asList(arr));
        return encodeString(value.toString());
    }

    private static String encodeString(String s) {
        var sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }

    private static String encodeMap(Map<?, ?> map) {
        var sb = new StringBuilder("{");
        var entries = new ArrayList<>(map.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(",");
            var entry = entries.get(i);
            sb.append(encodeString(entry.getKey().toString()));
            sb.append(":");
            sb.append(encode(entry.getValue()));
        }
        return sb.append("}").toString();
    }

    private static String encodeList(List<?> list) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(encode(list.get(i)));
        }
        return sb.append("]").toString();
    }

    /** Decode a JSON string to Java objects (Map, List, String, Number, Boolean, null). */
    public static Object decode(String json) {
        if (json.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException(
                    "JSON input exceeds maximum length of " + MAX_INPUT_LENGTH + " bytes");
        }
        return new JsonParser(json.trim()).parseValue();
    }

    /** Decode a JSON string, expecting a JSON object (map). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeObject(String json) {
        var result = decode(json);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object, got: " + result);
    }

    private static final class JsonParser {
        private final String input;
        private int pos;
        private int depth;

        JsonParser(String input) {
            this.input = input;
            this.pos = 0;
            this.depth = 0;
        }

        private void pushDepth() {
            if (++depth > MAX_DEPTH) {
                throw new IllegalArgumentException(
                        "JSON nesting depth exceeds maximum of " + MAX_DEPTH);
            }
        }

        private void popDepth() {
            depth--;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) return null;

            char c = input.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            pushDepth();
            expect('{');
            var map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '}') {
                pos++;
                popDepth();
                return map;
            }
            while (true) {
                skipWhitespace();
                var key = parseString();
                skipWhitespace();
                expect(':');
                var value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            expect('}');
            popDepth();
            return map;
        }

        private List<Object> parseArray() {
            pushDepth();
            expect('[');
            var list = new ArrayList<Object>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ']') {
                pos++;
                popDepth();
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            expect(']');
            popDepth();
            return list;
        }

        private String parseString() {
            expect('"');
            var sb = new StringBuilder();
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= input.length()) break;
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > input.length()) {
                                throw new IllegalArgumentException(
                                        "Incomplete unicode escape at position " + (pos - 2));
                            }
                            var hex = input.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < input.length() && input.charAt(pos) == '-') pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            boolean isFloat = false;
            if (pos < input.length() && input.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            }
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            }
            String numStr = input.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            }
            long l = Long.parseLong(numStr);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        }

        private Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Unexpected token at position " + pos);
        }

        private Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Unexpected token at position " + pos);
        }

        private void expect(char c) {
            if (pos >= input.length() || input.charAt(pos) != c) {
                throw new IllegalArgumentException(
                        "Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}
