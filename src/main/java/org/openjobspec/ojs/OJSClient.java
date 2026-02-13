package org.openjobspec.ojs;

import org.openjobspec.ojs.transport.HttpTransport;
import org.openjobspec.ojs.transport.Transport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * OJS client for enqueuing jobs, managing workflows, and querying job status.
 *
 * <pre>{@code
 * var client = OJSClient.builder()
 *     .url("http://localhost:8080")
 *     .build();
 *
 * // Simple enqueue
 * var job = client.enqueue("email.send", Map.of("to", "user@example.com"));
 *
 * // Enqueue with options
 * var job = client.enqueue("report.generate", Map.of("id", 42))
 *     .queue("reports")
 *     .delay(Duration.ofMinutes(5))
 *     .retry(RetryPolicy.builder().maxAttempts(5).build())
 *     .send();
 * }</pre>
 */
public final class OJSClient {

    private final Transport transport;

    private OJSClient(Transport transport) {
        this.transport = transport;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Enqueue a job immediately with default options.
     *
     * @param type the job type (dot-namespaced, e.g. "email.send")
     * @param args the job arguments as a map
     * @return the created job
     */
    public Job enqueue(String type, Map<String, Object> args) {
        return new JobRequest(this, type, args).send();
    }

    /**
     * Create a job request builder for enqueuing with options.
     * Call {@code .send()} on the returned builder to enqueue.
     *
     * @param type the job type
     * @param args the job arguments
     * @return a job request builder
     */
    public JobRequest enqueue(String type, Object args) {
        @SuppressWarnings("unchecked")
        var argsMap = args instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of("_value", args);
        return new JobRequest(this, type, argsMap);
    }

    /** Internal: execute the enqueue HTTP request. */
    Job doEnqueue(Map<String, Object> request) {
        var response = transport.post("/jobs", request);
        return parseJob(response);
    }

    /**
     * Enqueue multiple jobs atomically.
     *
     * @param requests the job requests (use {@link #buildBatchRequest} to construct)
     * @return list of created jobs
     */
    public List<Job> enqueueBatch(List<Map<String, Object>> requests) {
        var body = Map.<String, Object>of("jobs", requests);
        var response = transport.post("/jobs/batch", body);
        return parseJobList(response);
    }

    /**
     * Get a job by ID.
     *
     * @param id the job ID (UUIDv7)
     * @return the job
     */
    public Job getJob(String id) {
        var response = transport.get("/jobs/" + id);
        return parseJob(response);
    }

    /**
     * Cancel a job.
     *
     * @param id the job ID
     * @return the cancelled job
     */
    public Job cancelJob(String id) {
        var response = transport.delete("/jobs/" + id);
        return parseJob(response);
    }

    /**
     * Check server health.
     *
     * @return health status as a map
     */
    public Map<String, Object> health() {
        return transport.get("/health");
    }

    /**
     * Get server conformance manifest.
     */
    public Map<String, Object> manifest() {
        // Manifest is at /ojs/manifest, not under /ojs/v1
        // The transport prepends /ojs/v1, so we need to work around
        return transport.get("/../manifest");
    }

    // --- Dead Letter Queue ---

    public List<Job> listDeadLetterJobs(String queue, int limit, int offset) {
        var path = "/dead-letter?limit=" + limit + "&offset=" + offset;
        if (queue != null) path += "&queue=" + queue;
        var response = transport.get(path);
        return parseJobList(response);
    }

    public Job retryDeadLetterJob(String id) {
        var response = transport.post("/dead-letter/" + id + "/retry", Map.of());
        return parseJob(response);
    }

    public void discardDeadLetterJob(String id) {
        transport.delete("/dead-letter/" + id);
    }

    // --- Cron Jobs ---

    public List<Map<String, Object>> listCronJobs() {
        var response = transport.get("/cron");
        @SuppressWarnings("unchecked")
        var crons = (List<Map<String, Object>>) response.get("cron_jobs");
        return crons != null ? crons : List.of();
    }

    public Map<String, Object> registerCronJob(Map<String, Object> cronJob) {
        return transport.post("/cron", cronJob);
    }

    public void unregisterCronJob(String name) {
        transport.delete("/cron/" + name);
    }

    // --- Queue Management ---

    public List<Map<String, Object>> listQueues() {
        var response = transport.get("/queues");
        @SuppressWarnings("unchecked")
        var queues = (List<Map<String, Object>>) response.get("queues");
        return queues != null ? queues : List.of();
    }

    public Map<String, Object> getQueueStats(String name) {
        return transport.get("/queues/" + name + "/stats");
    }

    public void pauseQueue(String name) {
        transport.post("/queues/" + name + "/pause", Map.of());
    }

    public void resumeQueue(String name) {
        transport.post("/queues/" + name + "/resume", Map.of());
    }

    // --- Workflows ---

    public Workflow.WorkflowStatus createWorkflow(Workflow.Definition definition) {
        var request = definition.toWire();
        var response = transport.post("/workflows", request);
        return Workflow.parseStatus(response);
    }

    public Workflow.WorkflowStatus getWorkflow(String id) {
        var response = transport.get("/workflows/" + id);
        return Workflow.parseStatus(response);
    }

    public void cancelWorkflow(String id) {
        transport.delete("/workflows/" + id);
    }

    // --- Internal Helpers ---

    @SuppressWarnings("unchecked")
    static Job parseJob(Map<String, Object> response) {
        var jobMap = response.containsKey("job")
                ? (Map<String, Object>) response.get("job")
                : response;

        return new Job(
                stringOr(jobMap, "specversion", Job.SPEC_VERSION),
                stringOr(jobMap, "id", null),
                stringOr(jobMap, "type", null),
                stringOr(jobMap, "queue", "default"),
                parseArgs(jobMap.get("args")),
                mapOr(jobMap, "meta"),
                intOr(jobMap, "priority", 0),
                intOr(jobMap, "timeout", 0),
                stringOr(jobMap, "scheduled_at", null),
                stringOr(jobMap, "expires_at", null),
                parseRetryPolicy(jobMap.get("retry")),
                null, // unique policy not returned by server
                stringOr(jobMap, "schema", null),
                stringOr(jobMap, "state", null),
                intOr(jobMap, "attempt", 0),
                stringOr(jobMap, "created_at", null),
                stringOr(jobMap, "enqueued_at", null),
                stringOr(jobMap, "started_at", null),
                stringOr(jobMap, "completed_at", null),
                parseJobError(jobMap.get("error")),
                jobMap.get("result"),
                stringListOr(jobMap, "tags")
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Job> parseJobList(Map<String, Object> response) {
        var jobsList = response.get("jobs");
        if (jobsList instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> parseJob((Map<String, Object>) o))
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> parseArgs(Object args) {
        if (args instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    static RetryPolicy parseRetryPolicy(Object retry) {
        if (retry == null) return null;
        if (!(retry instanceof Map<?, ?> map)) return null;

        return RetryPolicy.builder()
                .maxAttempts(intOr((Map<String, Object>) map, "max_attempts", 3))
                .initialInterval(durationFromMs(map, "initial_interval_ms", 1000))
                .backoffCoefficient(doubleOr((Map<String, Object>) map, "backoff_coefficient", 2.0))
                .maxInterval(durationFromMs(map, "max_interval_ms", 300_000))
                .jitter(boolOr((Map<String, Object>) map, "jitter", true))
                .nonRetryableErrors(stringListOr((Map<String, Object>) map, "non_retryable_errors"))
                .build();
    }

    @SuppressWarnings("unchecked")
    static Job.JobError parseJobError(Object error) {
        if (error == null) return null;
        if (!(error instanceof Map<?, ?> map)) return null;
        var m = (Map<String, Object>) map;
        return new Job.JobError(
                stringOr(m, "type", "unknown"),
                stringOr(m, "message", ""),
                stringListOr(m, "backtrace"),
                stringOr(m, "code", null),
                mapOr(m, "details")
        );
    }

    private static String stringOr(Map<String, Object> map, String key, String defaultValue) {
        var v = map.get(key);
        return v instanceof String s ? s : defaultValue;
    }

    private static int intOr(Map<String, Object> map, String key, int defaultValue) {
        var v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private static double doubleOr(Map<String, Object> map, String key, double defaultValue) {
        var v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    private static boolean boolOr(Map<String, Object> map, String key, boolean defaultValue) {
        var v = map.get(key);
        if (v instanceof Boolean b) return b;
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOr(Map<String, Object> map, String key) {
        var v = map.get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private static List<String> stringListOr(Map<String, Object> map, String key) {
        var v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private static Duration durationFromMs(Map<?, ?> map, String key, long defaultMs) {
        var v = map.get(key);
        if (v instanceof Number n) return Duration.ofMillis(n.longValue());
        return Duration.ofMillis(defaultMs);
    }

    // --- Builder ---

    public static final class Builder {
        private String url;
        private HttpClient httpClient;
        private String authToken;
        private Map<String, String> headers;

        private Builder() {}

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public OJSClient build() {
            Objects.requireNonNull(url, "url must not be null");
            var transport = HttpTransport.builder()
                    .url(url)
                    .httpClient(httpClient)
                    .authToken(authToken)
                    .headers(headers)
                    .build();
            return new OJSClient(transport);
        }
    }
}
