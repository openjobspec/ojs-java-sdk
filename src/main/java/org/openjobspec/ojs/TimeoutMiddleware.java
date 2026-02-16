package org.openjobspec.ojs;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Timeout middleware for job processing.
 *
 * <p>Enforces a maximum execution time for job handlers. If the handler
 * does not complete within the configured duration, the job is interrupted
 * and a {@link TimeoutException} is thrown.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * worker.use(TimeoutMiddleware.create(Duration.ofSeconds(30)));
 * }</pre>
 */
public final class TimeoutMiddleware {

    private TimeoutMiddleware() {
    }

    /**
     * Create a timeout middleware with the specified duration.
     *
     * @param timeout the maximum execution time
     * @return the middleware
     */
    public static Middleware create(Duration timeout) {
        return (ctx, next) -> {
            var future = CompletableFuture.runAsync(() -> {
                try {
                    next.handle(ctx);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException(String.format(
                        "Job %s (id=%s) timed out after %dms",
                        ctx.job().type(), ctx.job().id(), timeout.toMillis()));
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new RuntimeException(cause);
            }
        };
    }
}
