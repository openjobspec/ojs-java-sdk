package org.openjobspec.ojs.transport;

import org.openjobspec.ojs.OJSError.OJSException;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Transport decorator that retries failed requests when the error is retryable.
 *
 * <p>Uses exponential backoff with jitter. Only retries on
 * {@link OJSException} where {@code isRetryable()} returns {@code true}.
 *
 * <pre>{@code
 * var transport = RetryableTransport.builder()
 *     .delegate(httpTransport)
 *     .maxRetries(3)
 *     .initialBackoff(Duration.ofMillis(200))
 *     .build();
 * }</pre>
 */
public final class RetryableTransport implements Transport {

    private static final System.Logger logger = System.getLogger(RetryableTransport.class.getName());

    private final Transport delegate;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    private RetryableTransport(Builder builder) {
        this.delegate = builder.delegate;
        this.maxRetries = builder.maxRetries;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Map<String, Object> get(String path) {
        return withRetry(() -> delegate.get(path), "GET " + path);
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body) {
        return withRetry(() -> delegate.post(path, body), "POST " + path);
    }

    @Override
    public Map<String, Object> delete(String path) {
        return withRetry(() -> delegate.delete(path), "DELETE " + path);
    }

    @Override
    public Map<String, Object> getAbsolute(String absolutePath) {
        return withRetry(() -> delegate.getAbsolute(absolutePath), "GET " + absolutePath);
    }

    private Map<String, Object> withRetry(TransportCall call, String description) {
        OJSException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return call.execute();
            } catch (OJSException e) {
                lastException = e;

                if (!e.isRetryable() || attempt == maxRetries) {
                    throw e;
                }

                long backoffMs = calculateBackoff(attempt);
                logger.log(System.Logger.Level.DEBUG,
                        "Retrying {0} (attempt {1}/{2}, backoff {3}ms): {4}",
                        description, attempt + 1, maxRetries, backoffMs, e.getMessage());

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw lastException;
    }

    private long calculateBackoff(int attempt) {
        long baseMs = initialBackoff.toMillis() * (1L << attempt);
        long cappedMs = Math.min(baseMs, maxBackoff.toMillis());
        // Add jitter: 50-100% of the calculated backoff
        return cappedMs / 2 + ThreadLocalRandom.current().nextLong(cappedMs / 2 + 1);
    }

    @FunctionalInterface
    private interface TransportCall {
        Map<String, Object> execute();
    }

    public static final class Builder {
        private Transport delegate;
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofMillis(200);
        private Duration maxBackoff = Duration.ofSeconds(10);

        private Builder() {}

        /** The underlying transport to delegate to. */
        public Builder delegate(Transport delegate) {
            this.delegate = delegate;
            return this;
        }

        /** Maximum number of retries (default: 3). Set to 0 to disable retries. */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Initial backoff duration before first retry (default: 200ms). */
        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        /** Maximum backoff duration cap (default: 10s). */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public RetryableTransport build() {
            Objects.requireNonNull(delegate, "delegate transport must not be null");
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
            Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
            return new RetryableTransport(this);
        }
    }
}
