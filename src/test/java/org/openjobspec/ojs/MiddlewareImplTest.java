package org.openjobspec.ojs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Common Middleware Implementations")
class MiddlewareImplTest {

    @Nested
    @DisplayName("LoggingMiddleware")
    class LoggingTests {

        @Test
        void completesSuccessfully() throws Exception {
            var mw = LoggingMiddleware.create();

            // Should not throw
            mw.apply(ctx("test.job", "default"), ctx -> "ok");
        }

        @Test
        void propagatesErrors() {
            var mw = LoggingMiddleware.create();

            assertThrows(RuntimeException.class, () ->
                    mw.apply(ctx("test.job", "default"),
                            ctx -> { throw new RuntimeException("boom"); }));
        }
    }

    @Nested
    @DisplayName("TimeoutMiddleware")
    class TimeoutTests {

        @Test
        void passesWhenJobCompletesInTime() throws Exception {
            var mw = TimeoutMiddleware.create(Duration.ofSeconds(5));

            mw.apply(ctx("test.job", "default"), ctx -> "ok");
            // No exception = pass
        }

        @Test
        void throwsWhenJobExceedsTimeout() {
            var mw = TimeoutMiddleware.create(Duration.ofMillis(10));

            assertThrows(java.util.concurrent.TimeoutException.class, () ->
                    mw.apply(ctx("test.job", "default"), ctx -> {
                        Thread.sleep(5000);
                        return "too late";
                    }));
        }
    }

    @Nested
    @DisplayName("RetryMiddleware")
    class RetryTests {

        @Test
        void passesOnSuccess() throws Exception {
            var mw = RetryMiddleware.create();

            mw.apply(ctx("test.job", "default"), ctx -> "ok");
        }

        @Test
        void retriesAndSucceeds() throws Exception {
            var calls = new AtomicInteger();
            var mw = RetryMiddleware.builder()
                    .maxRetries(3)
                    .baseDelay(Duration.ofMillis(1))
                    .jitter(false)
                    .build();

            mw.apply(ctx("test.job", "default"), ctx -> {
                if (calls.incrementAndGet() < 3) {
                    throw new RuntimeException("fail");
                }
                return "ok";
            });

            assertEquals(3, calls.get());
        }

        @Test
        void throwsAfterExhaustingRetries() {
            var mw = RetryMiddleware.builder()
                    .maxRetries(2)
                    .baseDelay(Duration.ofMillis(1))
                    .jitter(false)
                    .build();

            assertThrows(RuntimeException.class, () ->
                    mw.apply(ctx("test.job", "default"),
                            ctx -> { throw new RuntimeException("always fails"); }));
        }
    }

    // -- Helpers --

    private static JobContext ctx(String jobType, String queue) {
        var job = new Job(
                Job.SPEC_VERSION, "test-id", jobType, queue,
                List.of(), Map.of(), 0, 0, null, null,
                null, null, null,
                "running", 1, null, null, null, null, null, null, null
        );
        return new JobContext(job, null, null, null);
    }
}
