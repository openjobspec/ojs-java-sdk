package org.openjobspec.ojs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * DurableJobHandler provides checkpoint-based crash recovery and deterministic
 * replay for long-running jobs.
 *
 * <p>Subclass this handler and use {@link #saveCheckpoint}, {@link #resumeCheckpoint},
 * and {@link #deleteCheckpoint} to persist intermediate state. If the worker crashes,
 * the job resumes from the last checkpoint instead of restarting from scratch.</p>
 *
 * <p>For deterministic execution across retries, use {@link #createDurableContext} to
 * obtain a {@link DurableContext}. Operations like {@link DurableContext#now()},
 * {@link DurableContext#random(int)}, and {@link DurableContext#sideEffect} are
 * recorded on first execution and replayed from the log on retry, ensuring identical
 * results regardless of when or how many times the job runs.</p>
 *
 * <pre>{@code
 * public class DataMigrationHandler extends DurableJobHandler {
 *     @Override
 *     public void handle(JobContext ctx) throws Exception {
 *         int processed = resumeCheckpoint(ctx, Integer.class).orElse(0);
 *         for (int i = processed; i < totalRows; i++) {
 *             processRow(i);
 *             if (i % 1000 == 0) saveCheckpoint(ctx, i);
 *         }
 *         deleteCheckpoint(ctx);
 *     }
 * }
 * }</pre>
 */
public abstract class DurableJobHandler implements JobHandler {

    private static final HttpClient CHECKPOINT_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    /**
     * Save a checkpoint for the current job. The state is serialized to JSON and
     * persisted server-side. Subsequent saves overwrite previous checkpoints.
     *
     * @param ctx   the job context
     * @param state the checkpoint state (must be JSON-serializable)
     * @throws IOException if the checkpoint cannot be saved
     */
    protected void saveCheckpoint(JobContext ctx, Object state) throws IOException {
        String json = JacksonSupport.isAvailable()
                ? JacksonSupport.serialize(state)
                : state.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ctx.serverUrl() + "/ojs/v1/jobs/" + ctx.job().id() + "/checkpoint"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"state\":" + json + "}"))
                .build();

        try {
            CHECKPOINT_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Checkpoint save interrupted", e);
        }
    }

    /**
     * Resume from the last checkpoint, if one exists.
     *
     * @param ctx   the job context
     * @param clazz the class to deserialize the checkpoint state into
     * @param <T>   the checkpoint state type
     * @return the checkpoint state, or empty if no checkpoint exists
     */
    protected <T> Optional<T> resumeCheckpoint(JobContext ctx, Class<T> clazz) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ctx.serverUrl() + "/ojs/v1/jobs/" + ctx.job().id() + "/checkpoint"))
                    .GET()
                    .build();

            HttpResponse<String> response = CHECKPOINT_CLIENT
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null) {
                String body = response.body();
                if (JacksonSupport.isAvailable()) {
                    return Optional.ofNullable(JacksonSupport.deserializeField(body, "state", clazz));
                }
            }
        } catch (Exception e) {
            // No checkpoint found — start fresh
        }
        return Optional.empty();
    }

    /**
     * Delete the checkpoint for the current job. Typically called after a job
     * completes successfully to clean up server-side state.
     *
     * <p>This method is lenient: a 404 response (no checkpoint found) is treated
     * as success. Other HTTP errors result in an {@link IOException}.</p>
     *
     * @param ctx the job context
     * @throws IOException if the server returns an unexpected error
     */
    protected void deleteCheckpoint(JobContext ctx) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ctx.serverUrl() + "/ojs/v1/jobs/" + ctx.job().id() + "/checkpoint"))
                .DELETE()
                .build();

        try {
            HttpResponse<String> response = CHECKPOINT_CLIENT
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400 && response.statusCode() != 404) {
                throw new IOException(
                        "Failed to delete checkpoint: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Checkpoint delete interrupted", e);
        }
    }

    /**
     * Create a new {@link DurableContext} for deterministic execution with no
     * existing replay log. Use this on the first execution of a job.
     *
     * @param ctx the job context
     * @return a new durable context with an empty replay log
     */
    protected DurableContext createDurableContext(JobContext ctx) {
        return new DurableContext(ctx, null);
    }

    /**
     * Create a {@link DurableContext} initialized with an existing replay log.
     * Use this when resuming from a checkpoint that contains a previously recorded log.
     *
     * @param ctx         the job context
     * @param existingLog the replay log from a prior execution, or null to start fresh
     * @return a durable context that will replay recorded entries before recording new ones
     */
    protected DurableContext createDurableContext(JobContext ctx,
                                                 List<Map<String, Object>> existingLog) {
        return new DurableContext(ctx, existingLog);
    }

    /**
     * Provides deterministic helpers for durable job execution.
     *
     * <p>All operations are recorded in a replay log on first execution. When the
     * job is retried, operations are replayed from the log in sequence order,
     * returning the same values that were produced on the original run. This
     * guarantees deterministic behavior across retries for inherently
     * non-deterministic operations like time, randomness, and external calls.</p>
     *
     * <p>The replay log should be persisted via {@link #saveCheckpoint} and
     * restored via {@link #resumeCheckpoint} between attempts.</p>
     *
     * <pre>{@code
     * public class IdempotentHandler extends DurableJobHandler {
     *     @Override
     *     public void handle(JobContext ctx) throws Exception {
     *         List<Map<String, Object>> log = resumeCheckpoint(ctx, List.class).orElse(null);
     *         DurableContext dc = createDurableContext(ctx, log);
     *
     *         Instant timestamp = dc.now();
     *         String token = dc.random(16);
     *         String result = dc.sideEffect("api-call", () -> callApi(token), String.class);
     *
     *         saveCheckpoint(ctx, dc.getReplayLog());
     *     }
     * }
     * }</pre>
     */
    public static final class DurableContext {

        private static final SecureRandom SECURE_RANDOM = new SecureRandom();
        private static final HexFormat HEX = HexFormat.of();

        private final JobContext ctx;
        private final List<Map<String, Object>> replayLog;
        private int seq;

        DurableContext(JobContext ctx, List<Map<String, Object>> existingLog) {
            this.ctx = ctx;
            this.replayLog = existingLog != null
                    ? new ArrayList<>(existingLog)
                    : new ArrayList<>();
            this.seq = 0;
        }

        /**
         * Returns a deterministic timestamp. On the first call during an
         * execution, records {@link Instant#now()} in the replay log. On
         * subsequent retries, returns the previously recorded instant.
         *
         * @return the deterministic current time
         */
        public Instant now() {
            if (seq < replayLog.size()) {
                Map<String, Object> entry = replayLog.get(seq);
                seq++;
                return Instant.parse((String) entry.get("result"));
            }
            Instant value = Instant.now();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seq", seq);
            entry.put("type", "now");
            entry.put("key", "now");
            entry.put("result", value.toString());
            replayLog.add(entry);
            seq++;
            return value;
        }

        /**
         * Returns a deterministic random hex string. On the first call,
         * generates {@code numBytes} of cryptographically secure random data
         * and hex-encodes it. On retry, returns the previously recorded value.
         *
         * @param numBytes the number of random bytes to generate
         * @return a hex-encoded string of length {@code numBytes * 2}
         */
        public String random(int numBytes) {
            if (seq < replayLog.size()) {
                Map<String, Object> entry = replayLog.get(seq);
                seq++;
                return (String) entry.get("result");
            }
            byte[] bytes = new byte[numBytes];
            SECURE_RANDOM.nextBytes(bytes);
            String hex = HEX.formatHex(bytes);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seq", seq);
            entry.put("type", "random");
            entry.put("key", "random");
            entry.put("result", hex);
            replayLog.add(entry);
            seq++;
            return hex;
        }

        /**
         * Execute a side effect deterministically. On the first call with the
         * given {@code key}, executes the callable and records the result in
         * the replay log. On retry, the callable is <em>not</em> re-executed;
         * instead, the recorded result is deserialized and returned.
         *
         * <p>When Jackson is available, the result is serialized to JSON for
         * storage and deserialized back to {@code clazz} on replay. Without
         * Jackson, the raw stored value is cast to the target type.</p>
         *
         * @param key   a unique identifier for this side effect within the job
         * @param fn    the callable to execute on first run
         * @param clazz the class to deserialize the result into on replay
         * @param <T>   the result type
         * @return the result of the callable (first run) or the replayed result (retry)
         * @throws Exception if the callable throws or deserialization fails
         */
        public <T> T sideEffect(String key, Callable<T> fn, Class<T> clazz) throws Exception {
            if (seq < replayLog.size()) {
                Map<String, Object> entry = replayLog.get(seq);
                seq++;
                Object stored = entry.get("result");
                if (JacksonSupport.isAvailable()) {
                    String json = (stored instanceof String s)
                            ? s
                            : JacksonSupport.serialize(stored);
                    return JacksonSupport.deserialize(json, clazz);
                }
                @SuppressWarnings("unchecked")
                T cast = (T) stored;
                return cast;
            }
            T result = fn.call();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("seq", seq);
            entry.put("type", "side_effect");
            entry.put("key", key);
            if (JacksonSupport.isAvailable()) {
                try {
                    entry.put("result", JacksonSupport.serialize(result));
                } catch (IOException e) {
                    entry.put("result", result);
                }
            } else {
                entry.put("result", result);
            }
            replayLog.add(entry);
            seq++;
            return result;
        }

        /**
         * Returns an unmodifiable view of the current replay log.
         * Persist this via {@link DurableJobHandler#saveCheckpoint} to enable
         * deterministic replay on retry.
         *
         * @return the replay log entries
         */
        public List<Map<String, Object>> getReplayLog() {
            return Collections.unmodifiableList(replayLog);
        }
    }
}
