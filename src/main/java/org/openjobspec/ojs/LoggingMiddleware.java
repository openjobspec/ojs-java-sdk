package org.openjobspec.ojs;

import java.time.Duration;
import java.time.Instant;

/**
 * Logging middleware for job processing instrumentation.
 *
 * <p>Logs job start, completion, and failure events with timing information
 * using {@link System.Logger} (part of java.base, no additional module required).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * worker.use(LoggingMiddleware.create());
 *
 * // With a custom logger
 * worker.use(LoggingMiddleware.create(System.getLogger("my.jobs")));
 * }</pre>
 */
public final class LoggingMiddleware {

    private LoggingMiddleware() {
    }

    /**
     * Create a logging middleware with the default OJS logger.
     *
     * @return the middleware
     */
    public static Middleware create() {
        return create(System.getLogger("org.openjobspec.ojs"));
    }

    /**
     * Create a logging middleware with a custom logger.
     *
     * @param logger the logger to use
     * @return the middleware
     */
    public static Middleware create(System.Logger logger) {
        return (ctx, next) -> {
            var jobType = ctx.job().type();
            var jobId = ctx.job().id();
            var attempt = ctx.attempt();

            logger.log(System.Logger.Level.DEBUG, "Job started: {0} (id={1}, attempt={2})",
                    jobType, jobId, attempt);

            var start = Instant.now();

            try {
                next.handle(ctx);
                var duration = Duration.between(start, Instant.now());

                logger.log(System.Logger.Level.INFO, "Job completed: {0} (id={1}, {2}ms)",
                        jobType, jobId, duration.toMillis());
            } catch (Exception e) {
                var duration = Duration.between(start, Instant.now());

                logger.log(System.Logger.Level.ERROR,
                        "Job failed: %s (id=%s, %dms): %s".formatted(
                                jobType, jobId, duration.toMillis(), e.getMessage()),
                        e);
                throw e;
            }
        };
    }
}
