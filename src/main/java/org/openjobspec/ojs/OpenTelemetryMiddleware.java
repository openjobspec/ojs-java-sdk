package org.openjobspec.ojs;

import java.time.Duration;
import java.time.Instant;

/**
 * OpenTelemetry middleware for the OJS Java SDK.
 *
 * <p>Provides execution middleware that instruments job processing with
 * OpenTelemetry traces and metrics, following the OJS Observability spec.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * import org.openjobspec.ojs.OpenTelemetryMiddleware;
 * import io.opentelemetry.api.GlobalOpenTelemetry;
 *
 * var otel = GlobalOpenTelemetry.get();
 * worker.use(OpenTelemetryMiddleware.create(otel));
 * }</pre>
 *
 * <h2>Prerequisites</h2>
 * <p>Add {@code io.opentelemetry:opentelemetry-api} to your dependencies.
 *
 * @see <a href="spec/ojs-observability.md">OJS Observability Spec</a>
 */
public final class OpenTelemetryMiddleware {

    private static final String INSTRUMENTATION_NAME = "org.openjobspec.ojs";

    private OpenTelemetryMiddleware() {}

    /**
     * Creates OpenTelemetry middleware using generic interfaces.
     *
     * <p>This version uses a simple callback pattern that works with any
     * OpenTelemetry implementation. For direct OTel API integration, wrap
     * this middleware or use the callback hooks.
     *
     * @param hooks the telemetry hooks for recording spans and metrics
     * @return middleware that instruments job processing
     */
    public static Middleware create(TelemetryHooks hooks) {
        return (ctx, next) -> {
            var jobType = ctx.job().type();
            var jobId = ctx.job().id();
            var queue = ctx.job().queue();
            var attempt = ctx.job().attempt();

            hooks.spanStart(jobType, jobId, queue, attempt);

            var start = Instant.now();
            try {
                var result = next.handle(ctx);
                var duration = Duration.between(start, Instant.now());

                hooks.spanEnd(jobType, queue, duration, null);
                hooks.recordCompleted(jobType, queue, duration);
                return result;
            } catch (Exception e) {
                var duration = Duration.between(start, Instant.now());

                hooks.spanEnd(jobType, queue, duration, e);
                hooks.recordFailed(jobType, queue, duration);
                throw e;
            }
        };
    }

    /**
     * Telemetry hooks for recording OpenTelemetry data.
     *
     * <p>Implement this interface to connect the OJS middleware to your
     * OpenTelemetry SDK setup. A reference implementation using the OTel
     * Java API is provided in the companion module.
     */
    public interface TelemetryHooks {
        /**
         * Called when a job span starts.
         *
         * @param jobType the job type (e.g., "email.send")
         * @param jobId   the job ID (UUIDv7)
         * @param queue   the queue name
         * @param attempt the current attempt number
         */
        void spanStart(String jobType, String jobId, String queue, int attempt);

        /**
         * Called when a job span ends.
         *
         * @param jobType  the job type
         * @param queue    the queue name
         * @param duration the execution duration
         * @param error    the error if the job failed, or null on success
         */
        void spanEnd(String jobType, String queue, Duration duration, Exception error);

        /**
         * Records a successful job completion metric.
         *
         * @param jobType  the job type
         * @param queue    the queue name
         * @param duration the execution duration
         */
        void recordCompleted(String jobType, String queue, Duration duration);

        /**
         * Records a failed job metric.
         *
         * @param jobType  the job type
         * @param queue    the queue name
         * @param duration the execution duration
         */
        void recordFailed(String jobType, String queue, Duration duration);
    }
}
