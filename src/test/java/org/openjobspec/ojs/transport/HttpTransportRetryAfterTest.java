package org.openjobspec.ojs.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpTransport#parseRetryAfter(HttpResponse)}.
 */
class HttpTransportRetryAfterTest {

    @Nested
    @DisplayName("parseRetryAfter")
    class ParseRetryAfterTests {

        @Test
        void parsesIntegerSeconds() {
            var response = stubResponse(Map.of("Retry-After", List.of("120")));
            assertEquals(120, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void parsesZeroSeconds() {
            var response = stubResponse(Map.of("Retry-After", List.of("0")));
            assertEquals(0, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void returnsMinusOneWhenHeaderMissing() {
            var response = stubResponse(Map.of());
            assertEquals(-1, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void returnsMinusOneForEmptyHeader() {
            var response = stubResponse(Map.of("Retry-After", List.of("")));
            assertEquals(-1, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void returnsMinusOneForUnparseableValue() {
            var response = stubResponse(Map.of("Retry-After", List.of("not-a-number-or-date")));
            assertEquals(-1, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void parsesHttpDateFormat() {
            // Use a date 60 seconds in the future
            var futureDate = ZonedDateTime.now().plusSeconds(60);
            var formatted = futureDate.format(DateTimeFormatter.RFC_1123_DATE_TIME);
            var response = stubResponse(Map.of("Retry-After", List.of(formatted)));

            long result = HttpTransport.parseRetryAfter(response);
            // Should be approximately 60s (allow some tolerance for test execution time)
            assertTrue(result >= 55 && result <= 65,
                    "Expected ~60s but got " + result);
        }

        @Test
        void httpDateInPastReturnsZero() {
            var pastDate = ZonedDateTime.now().minusSeconds(60);
            var formatted = pastDate.format(DateTimeFormatter.RFC_1123_DATE_TIME);
            var response = stubResponse(Map.of("Retry-After", List.of(formatted)));

            assertEquals(0, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void negativeSec() {
            var response = stubResponse(Map.of("Retry-After", List.of("-5")));
            assertEquals(0, HttpTransport.parseRetryAfter(response));
        }

        @Test
        void handlesWhitespaceAroundSeconds() {
            var response = stubResponse(Map.of("Retry-After", List.of("  30  ")));
            assertEquals(30, HttpTransport.parseRetryAfter(response));
        }
    }

    /** Minimal HttpResponse stub for testing header parsing. */
    private static HttpResponse<String> stubResponse(Map<String, List<String>> headerMap) {
        var headers = HttpHeaders.of(headerMap, (k, v) -> true);
        return new HttpResponse<>() {
            @Override public int statusCode() { return 429; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return headers; }
            @Override public String body() { return ""; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://localhost"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
