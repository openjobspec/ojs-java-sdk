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

    private final String rawBaseUrl;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final String authToken;
    private final Map<String, String> customHeaders;
    private final Duration requestTimeout;

    private HttpTransport(Builder builder) {
        this.rawBaseUrl = builder.url.replaceAll("/+$", "");
        this.baseUrl = rawBaseUrl + BASE_PATH;
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

    @Override
    public Map<String, Object> getAbsolute(String absolutePath) {
        return doRequest("GET", rawBaseUrl + absolutePath, null);
    }

    private Map<String, Object> execute(String method, String path, Map<String, Object> body) {
        return doRequest(method, baseUrl + path, body);
    }

    private Map<String, Object> doRequest(String method, String url, Map<String, Object> body) {
        try {
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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

}
