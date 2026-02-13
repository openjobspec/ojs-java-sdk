package org.openjobspec.ojs.transport;

import org.openjobspec.ojs.OJSError;
import org.openjobspec.ojs.OJSError.OJSException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * HTTP transport implementation using {@link java.net.http.HttpClient}.
 *
 * <p>Handles JSON serialization/deserialization, OJS headers, and error parsing
 * with zero external dependencies.
 */
public final class HttpTransport implements Transport {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final String authToken;
    private final Map<String, String> customHeaders;
    private final Duration requestTimeout;

    private HttpTransport(Builder builder) {
        this.baseUrl = builder.url.replaceAll("/+$", "") + BASE_PATH;
        this.httpClient = builder.httpClient != null ? builder.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                        .build();
        this.authToken = builder.authToken;
        this.customHeaders = builder.customHeaders != null
                ? Map.copyOf(builder.customHeaders) : Map.of();
        this.requestTimeout = builder.requestTimeout != null
                ? builder.requestTimeout : DEFAULT_REQUEST_TIMEOUT;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Map<String, Object> get(String path) {
        return execute("GET", path, null);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body) {
        return execute("POST", path, body);
    }

    @Override
    public Map<String, Object> delete(String path) {
        return execute("DELETE", path, null);
    }

    private Map<String, Object> execute(String method, String path, Map<String, Object> body) {
        try {
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", OJS_CONTENT_TYPE)
                    .header("Accept", OJS_CONTENT_TYPE)
                    .header("OJS-Version", OJS_VERSION);

            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            customHeaders.forEach(requestBuilder::header);

            if (body != null) {
                requestBuilder.method(method,
                        HttpRequest.BodyPublishers.ofString(Json.encode(body)));
            } else if ("POST".equals(method)) {
                requestBuilder.method(method,
                        HttpRequest.BodyPublishers.ofString("{}"));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            var response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new OJSException(parseError(response));
            }

            var responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return Map.of();
            }

            var parsed = Json.decode(responseBody);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                var result = (Map<String, Object>) map;
                return result;
            }
            return Map.of("_raw", parsed);

        } catch (OJSException e) {
            throw e;
        } catch (IOException e) {
            throw new OJSException(new OJSError.TransportError(
                    "transport_error", "HTTP request failed: " + e.getMessage(), e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OJSException(new OJSError.TransportError(
                    "transport_error", "HTTP request interrupted", e));
        }
    }

    private OJSError parseError(HttpResponse<String> response) {
        var body = response.body();
        int status = response.statusCode();

        if (body != null && !body.isBlank()) {
            try {
                var parsed = Json.decode(body);
                if (parsed instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    var errorObj = (Map<String, Object>) map.get("error");
                    if (errorObj != null) {
                        var code = stringOrDefault(errorObj.get("code"), "unknown");
                        var message = stringOrDefault(errorObj.get("message"), body);
                        var retryable = Boolean.TRUE.equals(errorObj.get("retryable"));
                        @SuppressWarnings("unchecked")
                        var details = errorObj.get("details") instanceof Map<?, ?> d
                                ? (Map<String, Object>) d : Map.<String, Object>of();
                        var requestId = response.headers()
                                .firstValue("X-Request-Id").orElse(null);

                        return new OJSError.ApiError(code, message, retryable,
                                details, requestId, status);
                    }
                }
            } catch (Exception ignored) {
                // Fall through to generic error
            }
        }

        return new OJSError.ApiError("http_" + status,
                "HTTP " + status + (body != null ? ": " + body : ""),
                status == 429 || status >= 500, Map.of(), null, status);
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value instanceof String s ? s : defaultValue;
    }

    public static final class Builder {
        private String url;
        private HttpClient httpClient;
        private String authToken;
        private Map<String, String> customHeaders;
        private Duration requestTimeout;

        private Builder() {}

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.customHeaders = headers;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public HttpTransport build() {
            Objects.requireNonNull(url, "url must not be null");
            return new HttpTransport(this);
        }
    }

    /**
     * Minimal JSON encoder/decoder using only the standard library.
     * Handles maps, lists, strings, numbers, booleans, and null.
     */
    public static final class Json {

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
            return new JsonParser(json.trim()).parseValue();
        }

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

            JsonParser(String input) {
                this.input = input;
                this.pos = 0;
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
                expect('{');
                var map = new LinkedHashMap<String, Object>();
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == '}') {
                    pos++;
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
                return map;
            }

            private List<Object> parseArray() {
                expect('[');
                var list = new ArrayList<Object>();
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ']') {
                    pos++;
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
}
