package org.openjobspec.ojs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Server-Sent Events (SSE) subscription for real-time OJS job events.
 *
 * <pre>{@code
 * var sub = SSESubscription.subscribe(
 *     "http://localhost:8080",
 *     "queue:default",
 *     event -> System.out.println("Event: " + event.type() + " — " + event.data())
 * );
 *
 * // Later: unsubscribe
 * sub.cancel();
 * }</pre>
 */
public final class SSESubscription implements AutoCloseable {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CompletableFuture<Void> done;
    private volatile Thread readerThread;

    private SSESubscription(CompletableFuture<Void> done) {
        this.done = done;
    }

    /** Stop receiving events and close the connection. */
    public void cancel() {
        cancelled.set(true);
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    @Override
    public void close() {
        cancel();
    }

    /** Wait for the subscription to complete (blocks until cancelled or disconnected). */
    public void await() {
        done.join();
    }

    /**
     * Subscribe to an SSE event stream.
     *
     * @param serverUrl Base URL of the OJS server.
     * @param channel   SSE channel (e.g., "job:&lt;id&gt;", "queue:&lt;name&gt;").
     * @param handler   Callback invoked for each event.
     * @return A subscription handle. Call {@link #cancel()} to disconnect.
     */
    public static SSESubscription subscribe(String serverUrl, String channel, Consumer<SSEEvent> handler) {
        return subscribe(serverUrl, channel, null, handler);
    }

    /**
     * Subscribe to an SSE event stream with authentication.
     *
     * @param serverUrl Base URL of the OJS server.
     * @param channel   SSE channel.
     * @param authToken Bearer auth token (nullable).
     * @param handler   Callback invoked for each event.
     * @return A subscription handle.
     */
    public static SSESubscription subscribe(String serverUrl, String channel, String authToken, Consumer<SSEEvent> handler) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SSESubscription sub = new SSESubscription(future);

        Thread thread = Thread.ofVirtual().name("ojs-sse-" + channel).start(() -> {
            sub.readerThread = Thread.currentThread();
            try {
                String encodedChannel = URLEncoder.encode(channel, StandardCharsets.UTF_8);
                URI uri = URI.create(serverUrl.replaceAll("/+$", "") + "/ojs/v1/events/stream?channel=" + encodedChannel);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("Cache-Control", "no-cache");
                if (authToken != null && !authToken.isBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                }
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(0); // no read timeout for SSE

                try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String eventType = "";
                    String eventId = "";
                    String eventData = "";

                    String line;
                    while (!sub.cancelled.get() && (line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (!eventData.isEmpty()) {
                                handler.accept(new SSEEvent(eventId, eventType.isEmpty() ? "message" : eventType, eventData));
                            }
                            eventType = "";
                            eventId = "";
                            eventData = "";
                        } else if (line.startsWith("event:")) {
                            eventType = line.substring(6).stripLeading();
                        } else if (line.startsWith("id:")) {
                            eventId = line.substring(3).stripLeading();
                        } else if (line.startsWith("data:")) {
                            String chunk = line.substring(5).stripLeading();
                            eventData = eventData.isEmpty() ? chunk : eventData + "\n" + chunk;
                        }
                    }
                } finally {
                    conn.disconnect();
                }
                future.complete(null);
            } catch (Exception e) {
                if (!sub.cancelled.get()) {
                    future.completeExceptionally(e);
                } else {
                    future.complete(null);
                }
            }
        });

        return sub;
    }

    /** Subscribe to events for a specific job. */
    public static SSESubscription subscribeJob(String serverUrl, String jobId, Consumer<SSEEvent> handler) {
        return subscribe(serverUrl, "job:" + jobId, handler);
    }

    /** Subscribe to events for all jobs in a queue. */
    public static SSESubscription subscribeQueue(String serverUrl, String queue, Consumer<SSEEvent> handler) {
        return subscribe(serverUrl, "queue:" + queue, handler);
    }

    /**
     * An SSE event from the OJS server.
     *
     * @param id   Event ID (for resume with Last-Event-ID).
     * @param type Event type (e.g., "job.state_changed").
     * @param data Raw event data (typically JSON).
     */
    public record SSEEvent(String id, String type, String data) {
    }
}
