package org.openjobspec.ojs.transport;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for HTTP transport-level retries with Retry-After backoff.
 *
 * @param maxRetries  maximum number of retries (default: 3)
 * @param minBackoff  minimum backoff duration before first retry (default: 500ms)
 * @param maxBackoff  maximum backoff duration cap (default: 30s)
 * @param enabled     whether retries are enabled (default: true)
 */
public record RetryConfig(
        int maxRetries,
        Duration minBackoff,
        Duration maxBackoff,
        boolean enabled
) {

    public RetryConfig {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        Objects.requireNonNull(minBackoff, "minBackoff must not be null");
        Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
    }

    /** Returns a default retry configuration: 3 retries, 500ms–30s backoff, enabled. */
    public static RetryConfig defaults() {
        return new RetryConfig(3, Duration.ofMillis(500), Duration.ofSeconds(30), true);
    }

    /** Returns a configuration with retries disabled. */
    public static RetryConfig disabled() {
        return new RetryConfig(0, Duration.ofMillis(500), Duration.ofSeconds(30), false);
    }
}
