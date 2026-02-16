///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.openjobspec:ojs-sdk:0.1.0

import org.openjobspec.ojs.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstrates OJS middleware for worker-side job processing.
 *
 * <p>Middleware wraps job handlers in an onion model. First-registered middleware
 * executes outermost. Each middleware calls {@code next.handle(ctx)} to pass
 * control inward.
 *
 * <p>Execution order for [logging, metrics, tracing]:
 * <pre>
 *   logging.before → metrics.before → tracing.before → handler
 *   → tracing.after → metrics.after → logging.after
 * </pre>
 *
 * <p>Prerequisites: An OJS-compatible server running at http://localhost:8080.
 */
public class MiddlewareExample {

    // --- Simple in-memory metrics collector ---

    static final AtomicLong completedCount = new AtomicLong();
    static final AtomicLong failedCount = new AtomicLong();

    public static void main(String[] args) {

        // ------------------------------------------------------------------
        // Client-side: inject trace context when enqueuing
        // ------------------------------------------------------------------
        // Note: The Java SDK does not have a client-side middleware chain.
        // Use a helper method to wrap enqueue calls instead.

        var client = OJSClient.builder()
                .url("http://localhost:8080")
                .build();

        var traceId = "trace_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        var job = client.enqueue("email.send", (Object) Map.of("to", "user@example.com"))
                .queue("email")
                .meta(Map.of(
                        "trace_id", traceId,
                        "enqueued_by", "my-service",
                        "enqueued_at_utc", Instant.now().toString()
                ))
                .send();

        System.out.printf("[enqueue] Created job %s with trace_id=%s%n", job.id(), traceId);

        // ------------------------------------------------------------------
        // Worker-side: composable middleware chain
        // ------------------------------------------------------------------

        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .queues(List.of("email", "default"))
                .concurrency(10)
                .build();

        // 1. Logging middleware (outermost — wraps entire chain)
        worker.use("logging", (ctx, next) -> {
            System.out.printf("[logging] Starting %s (id=%s, attempt=%d)%n",
                    ctx.job().type(), ctx.job().id(), ctx.attempt());
            var start = Instant.now();
            try {
                next.handle(ctx);
                var elapsed = Duration.between(start, Instant.now());
                System.out.printf("[logging] Completed %s in %dms%n",
                        ctx.job().type(), elapsed.toMillis());
            } catch (Exception e) {
                var elapsed = Duration.between(start, Instant.now());
                System.out.printf("[logging] Failed %s after %dms: %s%n",
                        ctx.job().type(), elapsed.toMillis(), e.getMessage());
                throw e;
            }
        });

        // 2. Metrics middleware (tracks counters and durations)
        worker.use("metrics", (ctx, next) -> {
            var start = Instant.now();
            try {
                next.handle(ctx);
                var durationMs = Duration.between(start, Instant.now()).toMillis();
                var total = completedCount.incrementAndGet();
                System.out.printf("[metrics] ojs.jobs.completed type=%s queue=%s duration=%dms total=%d%n",
                        ctx.job().type(), ctx.job().queue(), durationMs, total);
            } catch (Exception e) {
                var durationMs = Duration.between(start, Instant.now()).toMillis();
                var total = failedCount.incrementAndGet();
                System.out.printf("[metrics] ojs.jobs.failed type=%s queue=%s duration=%dms total=%d%n",
                        ctx.job().type(), ctx.job().queue(), durationMs, total);
                throw e;
            }
        });

        // 3. Trace context middleware (innermost — restores distributed trace)
        worker.use("trace-context", (ctx, next) -> {
            var meta = ctx.job().meta();
            if (meta != null && meta.containsKey("trace_id")) {
                var tid = meta.get("trace_id").toString();
                // In production, restore OpenTelemetry span context here.
                System.out.printf("[trace] Restoring trace context: %s%n", tid);
            }
            next.handle(ctx);
        });

        // ------------------------------------------------------------------
        // Register handlers
        // ------------------------------------------------------------------

        worker.register("email.send", ctx -> {
            var to = (String) ctx.job().argsMap().get("to");
            System.out.printf("  Sending email to %s%n", to);
            Thread.sleep(100);
            return Map.of(
                    "messageId", "msg_" + System.currentTimeMillis(),
                    "delivered", true
            );
        });

        worker.register("report.generate", ctx -> {
            var reportId = ctx.job().argsMap().get("id");
            System.out.printf("  Generating report %s (attempt %d)%n", reportId, ctx.attempt());
            ctx.heartbeat();
            Thread.sleep(500);
            return Map.of("url", "https://reports.example.com/" + reportId);
        });

        // ------------------------------------------------------------------
        // Start worker
        // ------------------------------------------------------------------

        System.out.println("Starting worker with middleware chain: logging → metrics → trace-context");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down worker...");
            worker.stop();
        }));

        worker.start();
    }
}
