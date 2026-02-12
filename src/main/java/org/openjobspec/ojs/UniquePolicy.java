package org.openjobspec.ojs;

import java.time.Duration;
import java.util.List;

/**
 * Unique job / deduplication policy as defined by the OJS Unique Jobs Specification.
 *
 * <p>Controls how duplicate jobs are detected and resolved. When attached to a job,
 * the backend checks for existing jobs that match the specified dimensions before inserting.
 */
public record UniquePolicy(
        List<String> key,
        Duration period,
        String onConflict,
        List<String> states
) {

    public static final List<String> DEFAULT_STATES = List.of(
            "available", "active", "scheduled", "retryable", "pending"
    );

    public UniquePolicy {
        if (key == null) key = List.of();
        if (onConflict == null) onConflict = "reject";
        if (states == null) states = DEFAULT_STATES;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> key = List.of();
        private Duration period;
        private String onConflict = "reject";
        private List<String> states = DEFAULT_STATES;

        private Builder() {}

        public Builder key(List<String> key) {
            this.key = key;
            return this;
        }

        public Builder period(Duration period) {
            this.period = period;
            return this;
        }

        public Builder onConflict(String onConflict) {
            this.onConflict = onConflict;
            return this;
        }

        public Builder states(List<String> states) {
            this.states = states;
            return this;
        }

        public UniquePolicy build() {
            return new UniquePolicy(key, period, onConflict, states);
        }
    }
}
