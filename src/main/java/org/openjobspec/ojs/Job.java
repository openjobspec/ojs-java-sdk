package org.openjobspec.ojs;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an OJS job envelope. Contains all required, optional, and system-managed attributes
 * as defined by the OJS Core Specification v1.0.0-rc.1.
 */
public record Job(
        String specversion,
        String id,
        String type,
        String queue,
        List<Object> args,
        Map<String, Object> meta,
        int priority,
        int timeout,
        String scheduledAt,
        String expiresAt,
        RetryPolicy retry,
        UniquePolicy unique,
        String schema,
        // System-managed
        String state,
        int attempt,
        String createdAt,
        String enqueuedAt,
        String startedAt,
        String completedAt,
        JobError error,
        Object result,
        List<String> tags
) {

    public static final String SPEC_VERSION = "1.0.0-rc.1";

    public Job {
        Objects.requireNonNull(type, "type must not be null");
        if (specversion == null) specversion = SPEC_VERSION;
        if (queue == null) queue = "default";
        if (args == null) args = List.of();
        if (meta == null) meta = Map.of();
        if (tags == null) tags = List.of();
    }

    /** Job lifecycle states as defined by the OJS Core Specification. */
    public enum State {
        SCHEDULED("scheduled"),
        AVAILABLE("available"),
        PENDING("pending"),
        ACTIVE("active"),
        COMPLETED("completed"),
        RETRYABLE("retryable"),
        CANCELLED("cancelled"),
        DISCARDED("discarded");

        private final String value;

        State(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == CANCELLED || this == DISCARDED;
        }

        public static State fromString(String s) {
            for (State state : values()) {
                if (state.value.equals(s)) return state;
            }
            throw new IllegalArgumentException("Unknown job state: " + s);
        }
    }

    /** Structured error as defined in OJS Core Specification Section 8. */
    public record JobError(
            String type,
            String message,
            List<String> backtrace,
            String code,
            Map<String, Object> details
    ) {
        public JobError {
            if (type == null) type = "unknown";
            if (message == null) message = "";
            if (backtrace == null) backtrace = List.of();
        }
    }

    /** Returns the args as a map, interpreting the first element as a map if present. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> argsMap() {
        if (args.isEmpty()) return Map.of();
        if (args.size() == 1 && args.getFirst() instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    /** Convenience to check if this job is in a terminal state. */
    public boolean isTerminal() {
        if (state == null) return false;
        return State.fromString(state).isTerminal();
    }
}
