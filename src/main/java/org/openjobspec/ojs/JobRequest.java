package org.openjobspec.ojs;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Fluent builder for enqueuing jobs with options.
 *
 * <pre>{@code
 * var job = client.enqueue("report.generate", Map.of("id", 42))
 *     .queue("reports")
 *     .delay(Duration.ofMinutes(5))
 *     .retry(RetryPolicy.builder().maxAttempts(5).build())
 *     .unique(UniquePolicy.builder().key(List.of("id")).period(Duration.ofHours(1)).build())
 *     .send();
 * }</pre>
 */
public final class JobRequest {

    private final OJSClient client;
    private final String type;
    private final Map<String, Object> args;

    private String queue = "default";
    private int priority = 0;
    private Duration timeout;
    private Instant scheduledAt;
    private Instant expiresAt;
    private RetryPolicy retry;
    private UniquePolicy unique;
    private Map<String, Object> meta;
    private List<String> tags;
    private String schema;

    JobRequest(OJSClient client, String type, Map<String, Object> args) {
        this.client = client;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.args = args != null ? args : Map.of();
    }

    /** Set the target queue. Default: "default". */
    public JobRequest queue(String queue) {
        this.queue = queue;
        return this;
    }

    /** Set the job priority. Higher values = higher priority. */
    public JobRequest priority(int priority) {
        this.priority = priority;
        return this;
    }

    /** Set the maximum execution timeout. */
    public JobRequest timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /** Delay execution by the specified duration from now. */
    public JobRequest delay(Duration delay) {
        this.scheduledAt = Instant.now().plus(delay);
        return this;
    }

    /** Schedule execution at a specific time. */
    public JobRequest scheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
        return this;
    }

    /** Set the expiration deadline. */
    public JobRequest expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    /** Set the retry policy. */
    public JobRequest retry(RetryPolicy retry) {
        this.retry = retry;
        return this;
    }

    /** Set the unique/deduplication policy. */
    public JobRequest unique(UniquePolicy unique) {
        this.unique = unique;
        return this;
    }

    /** Set job metadata. */
    public JobRequest meta(Map<String, Object> meta) {
        this.meta = meta;
        return this;
    }

    /** Set tags for filtering and observability. */
    public JobRequest tags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    /** Set the args schema URI. */
    public JobRequest schema(String schema) {
        this.schema = schema;
        return this;
    }

    /** Send the job to the server and return the created job. */
    public Job send() {
        return client.doEnqueue(buildRequest());
    }

    /** Build the wire-format request body. */
    Map<String, Object> buildRequest() {
        var request = new LinkedHashMap<String, Object>();
        request.put("type", type);
        request.put("args", List.of(args));

        if (meta != null && !meta.isEmpty()) {
            request.put("meta", meta);
        }
        if (schema != null) {
            request.put("schema", schema);
        }

        var options = new LinkedHashMap<String, Object>();
        options.put("queue", queue);
        if (priority != 0) {
            options.put("priority", priority);
        }
        if (timeout != null) {
            options.put("timeout_ms", timeout.toMillis());
        }
        if (scheduledAt != null) {
            options.put("delay_until", scheduledAt.toString());
        }
        if (expiresAt != null) {
            options.put("expires_at", expiresAt.toString());
        }
        if (retry != null) {
            options.put("retry", buildRetryWire(retry));
        }
        if (unique != null) {
            options.put("unique", buildUniqueWire(unique));
        }
        if (tags != null && !tags.isEmpty()) {
            options.put("tags", tags);
        }

        request.put("options", options);
        return request;
    }

    static Map<String, Object> buildRetryWire(RetryPolicy retry) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("max_attempts", retry.maxAttempts());
        wire.put("initial_interval_ms", retry.initialInterval().toMillis());
        wire.put("backoff_coefficient", retry.backoffCoefficient());
        wire.put("max_interval_ms", retry.maxInterval().toMillis());
        wire.put("jitter", retry.jitter());
        if (!retry.nonRetryableErrors().isEmpty()) {
            wire.put("non_retryable_errors", retry.nonRetryableErrors());
        }
        return wire;
    }

    static Map<String, Object> buildUniqueWire(UniquePolicy unique) {
        var wire = new LinkedHashMap<String, Object>();
        if (!unique.key().isEmpty()) {
            wire.put("key", unique.key());
        }
        if (unique.period() != null) {
            wire.put("period_ms", unique.period().toMillis());
        }
        wire.put("on_conflict", unique.onConflict());
        return wire;
    }
}
