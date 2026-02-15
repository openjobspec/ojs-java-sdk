package org.openjobspec.ojs;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics middleware for job processing instrumentation.
 *
 * <p>Tracks job execution counts, durations, successes, and failures per job type and queue.
 * Works as a standalone in-memory metrics collector or integrates with external systems
 * (e.g., Micrometer, Prometheus) via the {@link MetricsHooks} callback interface.
 *
 * <h2>Standalone usage (in-memory metrics)</h2>
 * <pre>{@code
 * var metrics = MetricsMiddleware.create();
 * worker.use(metrics.middleware());
 *
 * // Later, query metrics
 * var snapshot = metrics.snapshot();
 * System.out.println("Total completed: " + snapshot.totalCompleted());
 * }</pre>
 *
 * <h2>Integration with external metrics systems</h2>
 * <pre>{@code
 * // Using Micrometer
 * var registry = new SimpleMeterRegistry();
 * worker.use(MetricsMiddleware.create(new MetricsMiddleware.MetricsHooks() {
 *     public void onCompleted(String jobType, String queue, Duration duration) {
 *         Timer.builder("ojs.job.duration")
 *             .tag("type", jobType).tag("queue", queue).tag("status", "completed")
 *             .register(registry).record(duration);
 *     }
 *     public void onFailed(String jobType, String queue, Duration duration, Exception error) {
 *         Timer.builder("ojs.job.duration")
 *             .tag("type", jobType).tag("queue", queue).tag("status", "failed")
 *             .register(registry).record(duration);
 *     }
 * }).middleware());
 * }</pre>
 */
public final class MetricsMiddleware {

    private final MetricsHooks hooks;
    private final ConcurrentHashMap<String, LongAdder> completedCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> failedCounters = new ConcurrentHashMap<>();

    private MetricsMiddleware(MetricsHooks hooks) {
        this.hooks = hooks;
    }

    /**
     * Create a metrics middleware with in-memory counters only.
     *
     * @return the metrics middleware
     */
    public static MetricsMiddleware create() {
        return new MetricsMiddleware(null);
    }

    /**
     * Create a metrics middleware with external hooks for integration
     * with metrics systems like Micrometer or Prometheus.
     *
     * @param hooks the metrics hooks
     * @return the metrics middleware
     */
    public static MetricsMiddleware create(MetricsHooks hooks) {
        return new MetricsMiddleware(hooks);
    }

    /**
     * Get the middleware to register with a worker via {@code worker.use(...)}.
     *
     * @return the middleware
     */
    public Middleware middleware() {
        return (ctx, next) -> {
            var jobType = ctx.job().type();
            var queue = ctx.job().queue();
            var start = Instant.now();

            try {
                next.handle(ctx);
                var duration = Duration.between(start, Instant.now());

                completedCounters
                        .computeIfAbsent(metricsKey(jobType, queue), k -> new LongAdder())
                        .increment();

                if (hooks != null) {
                    hooks.onCompleted(jobType, queue, duration);
                }
            } catch (Exception e) {
                var duration = Duration.between(start, Instant.now());

                failedCounters
                        .computeIfAbsent(metricsKey(jobType, queue), k -> new LongAdder())
                        .increment();

                if (hooks != null) {
                    hooks.onFailed(jobType, queue, duration, e);
                }
                throw e;
            }
        };
    }

    /**
     * Get a snapshot of all metrics collected so far.
     *
     * @return an immutable snapshot of current metrics
     */
    public Snapshot snapshot() {
        long completed = completedCounters.values().stream().mapToLong(LongAdder::sum).sum();
        long failed = failedCounters.values().stream().mapToLong(LongAdder::sum).sum();

        Map<String, Long> completedByKey = new java.util.HashMap<>();
        completedCounters.forEach((k, v) -> completedByKey.put(k, v.sum()));

        Map<String, Long> failedByKey = new java.util.HashMap<>();
        failedCounters.forEach((k, v) -> failedByKey.put(k, v.sum()));

        return new Snapshot(completed, failed, Map.copyOf(completedByKey), Map.copyOf(failedByKey));
    }

    private static String metricsKey(String jobType, String queue) {
        return jobType + ":" + queue;
    }

    /**
     * Immutable snapshot of metrics at a point in time.
     *
     * @param totalCompleted   total completed job count
     * @param totalFailed      total failed job count
     * @param completedByKey   completed counts by "jobType:queue" key
     * @param failedByKey      failed counts by "jobType:queue" key
     */
    public record Snapshot(
            long totalCompleted,
            long totalFailed,
            Map<String, Long> completedByKey,
            Map<String, Long> failedByKey
    ) {
        /** Total number of jobs processed (completed + failed). */
        public long totalProcessed() {
            return totalCompleted + totalFailed;
        }
    }

    /**
     * Callback interface for integrating with external metrics systems.
     *
     * <p>Implement this to forward job metrics to Micrometer, Prometheus client,
     * or any other metrics library.
     */
    public interface MetricsHooks {

        /**
         * Called when a job completes successfully.
         *
         * @param jobType  the job type (e.g., "email.send")
         * @param queue    the queue name
         * @param duration the execution duration
         */
        void onCompleted(String jobType, String queue, Duration duration);

        /**
         * Called when a job fails with an exception.
         *
         * @param jobType  the job type
         * @param queue    the queue name
         * @param duration the execution duration
         * @param error    the exception that caused the failure
         */
        void onFailed(String jobType, String queue, Duration duration, Exception error);
    }
}
