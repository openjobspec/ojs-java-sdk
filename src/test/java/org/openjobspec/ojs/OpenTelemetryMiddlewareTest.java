package org.openjobspec.ojs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenTelemetryMiddleware")
class OpenTelemetryMiddlewareTest {

    @Nested
    @DisplayName("Successful job processing")
    class SuccessTests {

        @Test
        @DisplayName("calls spanStart with correct job attributes")
        void callsSpanStartWithCorrectAttributes() throws Exception {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);

            mw.apply(ctx("email.send", "email-queue", "job-123", 2), ctx -> "ok");

            assertEquals("email.send", hooks.startJobType.get());
            assertEquals("job-123", hooks.startJobId.get());
            assertEquals("email-queue", hooks.startQueue.get());
            assertEquals(2, hooks.startAttempt.get());
        }

        @Test
        @DisplayName("calls spanEnd with null error on success")
        void callsSpanEndWithNullError() throws Exception {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);

            mw.apply(ctx("email.send", "default", "job-1", 1), ctx -> "result");

            assertEquals("email.send", hooks.endJobType.get());
            assertEquals("default", hooks.endQueue.get());
            assertNotNull(hooks.endDuration.get());
            assertNull(hooks.endError.get());
        }

        @Test
        @DisplayName("calls recordCompleted on success")
        void callsRecordCompleted() throws Exception {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);

            mw.apply(ctx("report.generate", "reports", "job-2", 1), ctx -> null);

            assertEquals(1, hooks.completedCount.get());
            assertEquals("report.generate", hooks.completedJobType.get());
            assertEquals("reports", hooks.completedQueue.get());
            assertNotNull(hooks.completedDuration.get());
        }

        @Test
        @DisplayName("does not call recordFailed on success")
        void doesNotCallRecordFailed() throws Exception {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);

            mw.apply(ctx("test", "default", "job-3", 1), ctx -> "ok");

            assertEquals(0, hooks.failedCount.get());
        }
    }

    @Nested
    @DisplayName("Failed job processing")
    class FailureTests {

        @Test
        @DisplayName("calls spanEnd with error on failure")
        void callsSpanEndWithError() {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);
            var ex = new RuntimeException("handler error");

            assertThrows(RuntimeException.class,
                    () -> mw.apply(ctx("email.send", "default", "job-4", 1),
                            ctx -> { throw ex; }));

            assertEquals("email.send", hooks.endJobType.get());
            assertNotNull(hooks.endDuration.get());
            assertSame(ex, hooks.endError.get());
        }

        @Test
        @DisplayName("calls recordFailed on failure")
        void callsRecordFailed() {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);

            assertThrows(RuntimeException.class,
                    () -> mw.apply(ctx("sms.send", "high", "job-5", 3),
                            ctx -> { throw new RuntimeException("boom"); }));

            assertEquals(1, hooks.failedCount.get());
            assertEquals("sms.send", hooks.failedJobType.get());
            assertEquals("high", hooks.failedQueue.get());
            assertNotNull(hooks.failedDuration.get());
        }

        @Test
        @DisplayName("does not call recordCompleted on failure")
        void doesNotCallRecordCompleted() {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);

            assertThrows(RuntimeException.class,
                    () -> mw.apply(ctx("test", "default", "job-6", 1),
                            ctx -> { throw new RuntimeException(); }));

            assertEquals(0, hooks.completedCount.get());
        }

        @Test
        @DisplayName("re-throws the original exception")
        void rethrowsOriginalException() {
            var hooks = new RecordingHooks();
            var mw = OpenTelemetryMiddleware.create(hooks);
            var original = new IllegalStateException("original");

            var thrown = assertThrows(IllegalStateException.class,
                    () -> mw.apply(ctx("test", "default", "job-7", 1),
                            ctx -> { throw original; }));

            assertSame(original, thrown);
        }
    }

    @Nested
    @DisplayName("Hook invocation order")
    class OrderTests {

        @Test
        @DisplayName("spanStart is called before handler executes")
        void spanStartBeforeHandler() throws Exception {
            var order = new StringBuilder();
            var hooks = new OpenTelemetryMiddleware.TelemetryHooks() {
                @Override
                public void spanStart(String jobType, String jobId, String queue, int attempt) {
                    order.append("start,");
                }
                @Override
                public void spanEnd(String jobType, String queue, Duration duration, Exception error) {
                    order.append("end,");
                }
                @Override
                public void recordCompleted(String jobType, String queue, Duration duration) {
                    order.append("completed");
                }
                @Override
                public void recordFailed(String jobType, String queue, Duration duration) {
                    order.append("failed");
                }
            };

            var mw = OpenTelemetryMiddleware.create(hooks);
            mw.apply(ctx("test", "default", "job-8", 1), ctx -> {
                order.append("handler,");
                return null;
            });

            assertEquals("start,handler,end,completed", order.toString());
        }

        @Test
        @DisplayName("spanStart is called before handler on failure path")
        void spanStartBeforeHandlerOnFailure() {
            var order = new StringBuilder();
            var hooks = new OpenTelemetryMiddleware.TelemetryHooks() {
                @Override
                public void spanStart(String jobType, String jobId, String queue, int attempt) {
                    order.append("start,");
                }
                @Override
                public void spanEnd(String jobType, String queue, Duration duration, Exception error) {
                    order.append("end,");
                }
                @Override
                public void recordCompleted(String jobType, String queue, Duration duration) {
                    order.append("completed");
                }
                @Override
                public void recordFailed(String jobType, String queue, Duration duration) {
                    order.append("failed");
                }
            };

            var mw = OpenTelemetryMiddleware.create(hooks);
            assertThrows(RuntimeException.class,
                    () -> mw.apply(ctx("test", "default", "job-9", 1),
                            ctx -> { throw new RuntimeException(); }));

            assertEquals("start,end,failed", order.toString());
        }
    }

    // --- Helpers ---

    private static JobContext ctx(String jobType, String queue, String jobId, int attempt) {
        var job = new Job(
                Job.SPEC_VERSION, jobId, jobType, queue,
                List.of(), Map.of(), 0, 0, null, null,
                null, null, null,
                "active", attempt, null, null, null, null, null, null, null
        );
        return new JobContext(job, null, null, null);
    }

    /** Recording implementation of TelemetryHooks for test assertions. */
    private static class RecordingHooks implements OpenTelemetryMiddleware.TelemetryHooks {
        final AtomicReference<String> startJobType = new AtomicReference<>();
        final AtomicReference<String> startJobId = new AtomicReference<>();
        final AtomicReference<String> startQueue = new AtomicReference<>();
        final AtomicInteger startAttempt = new AtomicInteger(-1);

        final AtomicReference<String> endJobType = new AtomicReference<>();
        final AtomicReference<String> endQueue = new AtomicReference<>();
        final AtomicReference<Duration> endDuration = new AtomicReference<>();
        final AtomicReference<Exception> endError = new AtomicReference<>();

        final AtomicInteger completedCount = new AtomicInteger();
        final AtomicReference<String> completedJobType = new AtomicReference<>();
        final AtomicReference<String> completedQueue = new AtomicReference<>();
        final AtomicReference<Duration> completedDuration = new AtomicReference<>();

        final AtomicInteger failedCount = new AtomicInteger();
        final AtomicReference<String> failedJobType = new AtomicReference<>();
        final AtomicReference<String> failedQueue = new AtomicReference<>();
        final AtomicReference<Duration> failedDuration = new AtomicReference<>();

        @Override
        public void spanStart(String jobType, String jobId, String queue, int attempt) {
            startJobType.set(jobType);
            startJobId.set(jobId);
            startQueue.set(queue);
            startAttempt.set(attempt);
        }

        @Override
        public void spanEnd(String jobType, String queue, Duration duration, Exception error) {
            endJobType.set(jobType);
            endQueue.set(queue);
            endDuration.set(duration);
            endError.set(error);
        }

        @Override
        public void recordCompleted(String jobType, String queue, Duration duration) {
            completedCount.incrementAndGet();
            completedJobType.set(jobType);
            completedQueue.set(queue);
            completedDuration.set(duration);
        }

        @Override
        public void recordFailed(String jobType, String queue, Duration duration) {
            failedCount.incrementAndGet();
            failedJobType.set(jobType);
            failedQueue.set(queue);
            failedDuration.set(duration);
        }
    }
}
