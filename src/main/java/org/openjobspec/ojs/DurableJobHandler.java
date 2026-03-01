package org.openjobspec.ojs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * DurableJobHandler provides checkpoint-based crash recovery for long-running jobs.
 *
 * <p>Subclass this handler and use {@link #saveCheckpoint} and {@link #resumeCheckpoint}
 * to persist intermediate state. If the worker crashes, the job resumes from the
 * last checkpoint instead of restarting from scratch.</p>
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
 *     }
 * }
 * }</pre>
 */
public abstract class DurableJobHandler implements JobHandler {

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

        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null) {
                // Extract state field from checkpoint response
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
}
