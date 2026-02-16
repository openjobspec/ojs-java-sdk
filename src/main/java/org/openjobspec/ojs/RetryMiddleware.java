package org.openjobspec.ojs;

import java.time.Duration;

/**
 * Retry middleware for job processing with configurable exponential backoff.
 *
 * <p>Catches exceptions from downstream handlers and retries with increasing
 * delays. If all retries are exhausted, the final exception is rethrown.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Default: 3 retries with 100ms base delay
 * worker.use(RetryMiddleware.create());
 *
 * // Custom configuration
 * worker.use(RetryMiddleware.builder()
 *     .maxRetries(5)
 *     .baseDelay(Duration.ofMillis(200))
 *     .maxDelay(Duration.ofSeconds(60))
 *     .jitter(true)
 *     .build());
 * }</pre>
 */
public final class RetryMiddleware {

    private RetryMiddleware() {
    }

    /**
     * Create a retry middleware with default settings (3 retries, 100ms base delay).
     *
     * @return the middleware
     */
    public static Middleware create() {
        return builder().build();
    }

    /**
     * Create a new builder for configuring retry behavior.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for configuring retry middleware. */
    public static final class Builder {
        private int maxRetries = 3;
        private Duration baseDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(30);
        private boolean jitter = true;

        private Builder() {
        }

        /** Set the maximum number of retry attempts. */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Set the base delay for exponential backoff. */
        public Builder baseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
            return this;
        }

        /** Set the maximum delay between retries. */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        /** Whether to add random jitter to the delay. Defaults to {@code true}. */
        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        /** Build the retry middleware. */
        public Middleware build() {
            int retries = this.maxRetries;
            long baseMs = this.baseDelay.toMillis();
            long maxMs = this.maxDelay.toMillis();
            boolean useJitter = this.jitter;

            return (ctx, next) -> {
                Exception lastError = null;

                for (int attempt = 0; attempt <= retries; attempt++) {
                    try {
                        next.handle(ctx);
                        return;
                    } catch (Exception e) {
                        lastError = e;

                        if (attempt >= retries) {
                            break;
                        }

                        long exponentialDelay = baseMs * (1L << attempt);
                        long cappedDelay = Math.min(exponentialDelay, maxMs);
                        long finalDelay = useJitter
                                ? (long) (cappedDelay * (0.5 + Math.random() * 0.5))
                                : cappedDelay;

                        Thread.sleep(finalDelay);
                    }
                }

                throw lastError;
            };
        }
    }
}
