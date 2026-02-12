package org.openjobspec.ojs;

/**
 * Functional interface for execution middleware. Middleware wraps job execution,
 * enabling cross-cutting concerns like logging, metrics, error handling, and
 * distributed trace propagation.
 *
 * <p>Middleware follows the onion model (outer layers execute first):
 * <pre>{@code
 * worker.use((ctx, next) -> {
 *     System.out.printf("Processing %s%n", ctx.job().type());
 *     var start = Instant.now();
 *     next.handle(ctx);
 *     System.out.printf("Done in %dms%n",
 *         Duration.between(start, Instant.now()).toMillis());
 * });
 * }</pre>
 *
 * <p>Middleware MUST call {@code next.handle(ctx)} to continue the chain.
 * Failing to call next will prevent the job handler from executing.
 */
@FunctionalInterface
public interface Middleware {

    /**
     * Apply this middleware around the next handler in the chain.
     *
     * @param ctx  the job context
     * @param next the next handler in the middleware chain (eventually the actual handler)
     * @throws Exception if the middleware or downstream handler fails
     */
    void apply(JobContext ctx, JobHandler next) throws Exception;
}
