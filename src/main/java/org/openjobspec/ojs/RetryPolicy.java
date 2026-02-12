package org.openjobspec.ojs;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Retry policy configuration as defined by the OJS Retry Policy Specification.
 *
 * <p>Controls behavior when a job handler fails: how many times the job is re-attempted,
 * how long to wait between attempts, and what happens when all attempts are exhausted.
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialInterval,
        double backoffCoefficient,
        Duration maxInterval,
        boolean jitter,
        List<String> nonRetryableErrors,
        String onExhaustion
) {

    /** Default retry policy: 3 attempts, 1s initial, 2x exponential, 5m max, jitter on. */
    public static final RetryPolicy DEFAULT = new RetryPolicy(
            3, Duration.ofSeconds(1), 2.0, Duration.ofMinutes(5),
            true, List.of(), "discard"
    );

    public RetryPolicy {
        if (maxAttempts < 0) throw new IllegalArgumentException("maxAttempts must be >= 0");
        if (backoffCoefficient < 1.0) throw new IllegalArgumentException("backoffCoefficient must be >= 1.0");
        Objects.requireNonNull(initialInterval, "initialInterval must not be null");
        Objects.requireNonNull(maxInterval, "maxInterval must not be null");
        if (nonRetryableErrors == null) nonRetryableErrors = List.of();
        if (onExhaustion == null) onExhaustion = "discard";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofSeconds(1);
        private double backoffCoefficient = 2.0;
        private Duration maxInterval = Duration.ofMinutes(5);
        private boolean jitter = true;
        private List<String> nonRetryableErrors = List.of();
        private String onExhaustion = "discard";

        private Builder() {}

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialInterval(Duration initialInterval) {
            this.initialInterval = initialInterval;
            return this;
        }

        public Builder backoffCoefficient(double backoffCoefficient) {
            this.backoffCoefficient = backoffCoefficient;
            return this;
        }

        public Builder maxInterval(Duration maxInterval) {
            this.maxInterval = maxInterval;
            return this;
        }

        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public Builder nonRetryableErrors(List<String> nonRetryableErrors) {
            this.nonRetryableErrors = nonRetryableErrors;
            return this;
        }

        public Builder onExhaustion(String onExhaustion) {
            this.onExhaustion = onExhaustion;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(
                    maxAttempts, initialInterval, backoffCoefficient,
                    maxInterval, jitter, nonRetryableErrors, onExhaustion
            );
        }
    }
}
