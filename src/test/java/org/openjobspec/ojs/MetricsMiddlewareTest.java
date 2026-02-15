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

@DisplayName("MetricsMiddleware")
class MetricsMiddlewareTest {

    @Nested
    @DisplayName("In-memory counters")
    class InMemoryTests {

        @Test
        void tracksCompletedJobs() throws Exception {
            var metrics = MetricsMiddleware.create();
            var mw = metrics.middleware();

            mw.apply(ctx("email.send", "default"), ctx -> "ok");
            mw.apply(ctx("email.send", "default"), ctx -> "ok");
            mw.apply(ctx("sms.send", "high"), ctx -> "ok");

            var snap = metrics.snapshot();
            assertEquals(3, snap.totalCompleted());
            assertEquals(0, snap.totalFailed());
            assertEquals(3, snap.totalProcessed());
            assertEquals(2L, snap.completedByKey().get("email.send:default"));
            assertEquals(1L, snap.completedByKey().get("sms.send:high"));
        }

        @Test
        void tracksFailedJobs() throws Exception {
            var metrics = MetricsMiddleware.create();
            var mw = metrics.middleware();

            assertThrows(RuntimeException.class,
                    () -> mw.apply(ctx("email.send", "default"),
                            ctx -> { throw new RuntimeException("fail"); }));

            var snap = metrics.snapshot();
            assertEquals(0, snap.totalCompleted());
            assertEquals(1, snap.totalFailed());
            assertEquals(1L, snap.failedByKey().get("email.send:default"));
        }

        @Test
        void mixedSuccessAndFailure() throws Exception {
            var metrics = MetricsMiddleware.create();
            var mw = metrics.middleware();

            mw.apply(ctx("job.a", "q1"), ctx -> "ok");

            assertThrows(RuntimeException.class,
                    () -> mw.apply(ctx("job.a", "q1"),
                            ctx -> { throw new RuntimeException(); }));

            mw.apply(ctx("job.a", "q1"), ctx -> "ok");

            var snap = metrics.snapshot();
            assertEquals(2, snap.totalCompleted());
            assertEquals(1, snap.totalFailed());
            assertEquals(3, snap.totalProcessed());
        }

        @Test
        void snapshotIsImmutable() throws Exception {
            var metrics = MetricsMiddleware.create();
            var mw = metrics.middleware();

            mw.apply(ctx("test", "default"), ctx -> "ok");
            var snap1 = metrics.snapshot();

            mw.apply(ctx("test", "default"), ctx -> "ok");
            var snap2 = metrics.snapshot();

            assertEquals(1, snap1.totalCompleted());
            assertEquals(2, snap2.totalCompleted());
        }
    }

    @Nested
    @DisplayName("External hooks")
    class HooksTests {

        @Test
        void callsOnCompletedHook() throws Exception {
            var completedCount = new AtomicInteger();
            var capturedDuration = new AtomicReference<Duration>();

            var metrics = MetricsMiddleware.create(new MetricsMiddleware.MetricsHooks() {
                @Override
                public void onCompleted(String jobType, String queue, Duration duration) {
                    completedCount.incrementAndGet();
                    capturedDuration.set(duration);
                }

                @Override
                public void onFailed(String jobType, String queue, Duration duration, Exception error) {
                }
            });

            metrics.middleware().apply(ctx("test", "default"), ctx -> "ok");

            assertEquals(1, completedCount.get());
            assertNotNull(capturedDuration.get());
        }

        @Test
        void callsOnFailedHook() throws Exception {
            var failedCount = new AtomicInteger();
            var capturedError = new AtomicReference<Exception>();

            var metrics = MetricsMiddleware.create(new MetricsMiddleware.MetricsHooks() {
                @Override
                public void onCompleted(String jobType, String queue, Duration duration) {
                }

                @Override
                public void onFailed(String jobType, String queue, Duration duration, Exception error) {
                    failedCount.incrementAndGet();
                    capturedError.set(error);
                }
            });

            var ex = new RuntimeException("boom");
            assertThrows(RuntimeException.class,
                    () -> metrics.middleware().apply(ctx("test", "default"),
                            ctx -> { throw ex; }));

            assertEquals(1, failedCount.get());
            assertSame(ex, capturedError.get());
        }

        @Test
        void bothInMemoryAndHooksAreUpdated() throws Exception {
            var hookCalled = new AtomicInteger();

            var metrics = MetricsMiddleware.create(new MetricsMiddleware.MetricsHooks() {
                @Override
                public void onCompleted(String jobType, String queue, Duration duration) {
                    hookCalled.incrementAndGet();
                }

                @Override
                public void onFailed(String jobType, String queue, Duration duration, Exception error) {
                }
            });

            metrics.middleware().apply(ctx("test", "default"), ctx -> "ok");

            assertEquals(1, hookCalled.get());
            assertEquals(1, metrics.snapshot().totalCompleted());
        }
    }

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
