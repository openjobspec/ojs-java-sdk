package org.openjobspec.ojs.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjobspec.ojs.OJSError;
import org.openjobspec.ojs.OJSError.OJSException;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryableTransportTest {

    @Mock
    Transport delegate;

    // -----------------------------------------------------------------------
    // Builder validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        void builderRequiresDelegate() {
            assertThrows(NullPointerException.class,
                    () -> RetryableTransport.builder().build());
        }

        @Test
        void builderRejectsNegativeMaxRetries() {
            assertThrows(IllegalArgumentException.class,
                    () -> RetryableTransport.builder()
                            .delegate(delegate)
                            .maxRetries(-1)
                            .build());
        }

        @Test
        void builderAcceptsZeroMaxRetries() {
            var transport = RetryableTransport.builder()
                    .delegate(delegate)
                    .maxRetries(0)
                    .build();
            assertNotNull(transport);
        }

        @Test
        void builderAcceptsAllOptions() {
            var transport = RetryableTransport.builder()
                    .delegate(delegate)
                    .maxRetries(5)
                    .initialBackoff(Duration.ofMillis(100))
                    .maxBackoff(Duration.ofSeconds(5))
                    .enabled(true)
                    .build();
            assertNotNull(transport);
        }
    }

    // -----------------------------------------------------------------------
    // Retry behavior
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Retry behavior")
    class RetryTests {

        @Test
        void successOnFirstAttemptDoesNotRetry() {
            when(delegate.get("/health")).thenReturn(Map.of("status", "ok"));

            var transport = buildRetryable(3);
            var result = transport.get("/health");

            assertEquals("ok", result.get("status"));
            verify(delegate, times(1)).get("/health");
        }

        @Test
        void retriesOnRetryableError() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(retryableException("Connection reset"))
                    .thenReturn(Map.of("id", "1"));

            var transport = buildRetryable(3);
            var result = transport.get("/jobs/1");

            assertEquals("1", result.get("id"));
            verify(delegate, times(2)).get("/jobs/1");
        }

        @Test
        void retriesUpToMaxThenThrows() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(retryableException("Connection reset"));

            var transport = buildRetryable(2);

            assertThrows(OJSException.class, () -> transport.get("/jobs/1"));
            verify(delegate, times(3)).get("/jobs/1"); // 1 initial + 2 retries
        }

        @Test
        void doesNotRetryNonRetryableError() {
            when(delegate.post(eq("/jobs"), any()))
                    .thenThrow(nonRetryableException("invalid_request", "Bad payload"));

            var transport = buildRetryable(3);

            var ex = assertThrows(OJSException.class,
                    () -> transport.post("/jobs", Map.of()));
            assertFalse(ex.isRetryable());
            verify(delegate, times(1)).post(eq("/jobs"), any());
        }

        @Test
        void retriesPostRequests() {
            when(delegate.post(eq("/jobs"), any()))
                    .thenThrow(retryableException("Timeout"))
                    .thenReturn(Map.of("job", Map.of("id", "j1")));

            var transport = buildRetryable(3);
            var result = transport.post("/jobs", Map.of("type", "test"));

            assertNotNull(result);
            verify(delegate, times(2)).post(eq("/jobs"), any());
        }

        @Test
        void retriesDeleteRequests() {
            when(delegate.delete("/jobs/1"))
                    .thenThrow(retryableException("Error"))
                    .thenReturn(Map.of("id", "1"));

            var transport = buildRetryable(3);
            var result = transport.delete("/jobs/1");

            assertNotNull(result);
            verify(delegate, times(2)).delete("/jobs/1");
        }

        @Test
        void retriesGetAbsoluteRequests() {
            when(delegate.getAbsolute("/ojs/manifest"))
                    .thenThrow(retryableException("Error"))
                    .thenReturn(Map.of("name", "test"));

            var transport = buildRetryable(3);
            var result = transport.getAbsolute("/ojs/manifest");

            assertEquals("test", result.get("name"));
            verify(delegate, times(2)).getAbsolute("/ojs/manifest");
        }

        @Test
        void zeroMaxRetriesDisablesRetry() {
            when(delegate.get("/health"))
                    .thenThrow(retryableException("Error"));

            var transport = buildRetryable(0);

            assertThrows(OJSException.class, () -> transport.get("/health"));
            verify(delegate, times(1)).get("/health");
        }

        @Test
        void retriesRateLimitedApiErrors() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(new OJSException(new OJSError.ApiError(
                            "rate_limited", "Too many requests", true,
                            Map.of(), null, 429)))
                    .thenReturn(Map.of("id", "1"));

            var transport = buildRetryable(3);
            var result = transport.get("/jobs/1");

            assertEquals("1", result.get("id"));
            verify(delegate, times(2)).get("/jobs/1");
        }

        @Test
        void doesNotRetryNotFoundApiError() {
            when(delegate.get("/jobs/nonexistent"))
                    .thenThrow(new OJSException(new OJSError.ApiError(
                            "not_found", "Job not found", false,
                            Map.of(), null, 404)));

            var transport = buildRetryable(3);

            var ex = assertThrows(OJSException.class,
                    () -> transport.get("/jobs/nonexistent"));
            assertTrue(ex.isNotFound());
            verify(delegate, times(1)).get("/jobs/nonexistent");
        }
    }

    // -----------------------------------------------------------------------
    // Retry-After header support
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Retry-After backoff")
    class RetryAfterTests {

        @Test
        void retrysWith429AndRetryAfterHeader() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(rateLimitedWithRetryAfter(2))
                    .thenReturn(Map.of("id", "1"));

            var transport = buildRetryable(3);
            var result = transport.get("/jobs/1");

            assertEquals("1", result.get("id"));
            verify(delegate, times(2)).get("/jobs/1");
        }

        @Test
        void maxRetriesExhaustedWith429() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(rateLimitedWithRetryAfter(1));

            var transport = buildRetryable(2);

            var ex = assertThrows(OJSException.class,
                    () -> transport.get("/jobs/1"));
            assertTrue(ex.isRateLimited());
            verify(delegate, times(3)).get("/jobs/1"); // 1 initial + 2 retries
        }

        @Test
        void non429NotRetried() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(nonRetryableException("not_found", "Not found"));

            var transport = buildRetryable(3);

            assertThrows(OJSException.class, () -> transport.get("/jobs/1"));
            verify(delegate, times(1)).get("/jobs/1");
        }

        @Test
        void disabledRetryDoesNotRetry() {
            when(delegate.get("/health"))
                    .thenThrow(rateLimitedWithRetryAfter(1));

            var transport = RetryableTransport.builder()
                    .delegate(delegate)
                    .maxRetries(3)
                    .initialBackoff(Duration.ofMillis(1))
                    .maxBackoff(Duration.ofMillis(10))
                    .enabled(false)
                    .build();

            assertThrows(OJSException.class, () -> transport.get("/health"));
            verify(delegate, times(1)).get("/health");
        }

        @Test
        void retryAfterCappedByMaxBackoff() {
            // retryAfterSeconds=999 but maxBackoff=10ms; should not hang
            when(delegate.get("/jobs/1"))
                    .thenThrow(rateLimitedWithRetryAfter(999))
                    .thenReturn(Map.of("id", "1"));

            var transport = buildRetryable(1);
            var start = System.currentTimeMillis();
            var result = transport.get("/jobs/1");
            var elapsed = System.currentTimeMillis() - start;

            assertEquals("1", result.get("id"));
            // maxBackoff is 10ms, so this should be very fast
            assertTrue(elapsed < 1000, "Expected fast retry but took " + elapsed + "ms");
        }

        @Test
        void fallsBackToExponentialWithoutRetryAfter() {
            // Rate limited but retryAfterSeconds=-1 (no Retry-After header)
            when(delegate.get("/jobs/1"))
                    .thenThrow(new OJSException(new OJSError.ApiError(
                            "rate_limited", "Too many requests", true,
                            Map.of(), null, 429, -1)))
                    .thenReturn(Map.of("id", "1"));

            var transport = buildRetryable(3);
            var result = transport.get("/jobs/1");

            assertEquals("1", result.get("id"));
            verify(delegate, times(2)).get("/jobs/1");
        }
        @Test
        void interruptDuringSleepThrowsWithInterruptFlag() {
            when(delegate.get("/jobs/1"))
                    .thenThrow(rateLimitedWithRetryAfter(999));

            var transport = RetryableTransport.builder()
                    .delegate(delegate)
                    .maxRetries(3)
                    .initialBackoff(Duration.ofMillis(1))
                    .maxBackoff(Duration.ofSeconds(60))
                    .build();

            // Interrupt the current thread before calling — Thread.sleep will
            // throw InterruptedException immediately.
            Thread.currentThread().interrupt();

            var ex = assertThrows(OJSException.class, () -> transport.get("/jobs/1"));
            assertEquals("retry_interrupted", ex.code());
            assertTrue(Thread.currentThread().isInterrupted());

            // Clear the interrupt flag to avoid polluting other tests
            Thread.interrupted();
        }
    }

    // -----------------------------------------------------------------------
    // RetryConfig support
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("RetryConfig")
    class RetryConfigTests {

        @Test
        void wrapReturnsRetryableTransportWhenEnabled() {
            var config = RetryConfig.defaults();
            var transport = RetryableTransport.wrap(delegate, config);
            assertInstanceOf(RetryableTransport.class, transport);
        }

        @Test
        void wrapReturnsDelegateWhenDisabled() {
            var config = RetryConfig.disabled();
            var transport = RetryableTransport.wrap(delegate, config);
            assertSame(delegate, transport);
        }

        @Test
        void wrapReturnsDelegateWhenZeroRetries() {
            var config = new RetryConfig(0, Duration.ofMillis(100), Duration.ofSeconds(5), true);
            var transport = RetryableTransport.wrap(delegate, config);
            assertSame(delegate, transport);
        }

        @Test
        void retryConfigDefaultsAreValid() {
            var config = RetryConfig.defaults();
            assertEquals(3, config.maxRetries());
            assertEquals(Duration.ofMillis(500), config.minBackoff());
            assertEquals(Duration.ofSeconds(30), config.maxBackoff());
            assertTrue(config.enabled());
        }

        @Test
        void retryConfigRejectsNegativeMaxRetries() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RetryConfig(-1, Duration.ofMillis(100), Duration.ofSeconds(5), true));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RetryableTransport buildRetryable(int maxRetries) {
        return RetryableTransport.builder()
                .delegate(delegate)
                .maxRetries(maxRetries)
                .initialBackoff(Duration.ofMillis(1))
                .maxBackoff(Duration.ofMillis(10))
                .build();
    }

    private OJSException retryableException(String message) {
        return new OJSException(
                new OJSError.TransportError("transport_error", message, null));
    }

    private OJSException nonRetryableException(String code, String message) {
        return new OJSException(
                new OJSError.ApiError(code, message, false, Map.of(), null, 422));
    }

    private OJSException rateLimitedWithRetryAfter(long retryAfterSeconds) {
        return new OJSException(new OJSError.ApiError(
                "rate_limited", "Too many requests", true,
                Map.of(), null, 429, retryAfterSeconds));
    }
}
