///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.openjobspec:ojs-sdk:0.1.0

import org.openjobspec.ojs.*;

import java.time.*;
import java.util.*;

/**
 * Demonstrates OJS worker usage: registering job handlers, adding middleware
 * for logging and error recovery, and performing a graceful shutdown.
 *
 * <p>Prerequisites: An OJS-compatible server running at http://localhost:8080.
 */
public class WorkerProcessing {

    public static void main(String[] args) {
        // Build a worker that polls two queues with up to 10 concurrent jobs
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .queues(List.of("default", "email"))
                .concurrency(10)
                .build();

        // --- Register handlers ---

        worker.register("email.send", ctx -> {
            var to = (String) ctx.job().argsMap().get("to");
            System.out.println("Sending email to: " + to);
            // Simulate work
            Thread.sleep(100);
            return Map.of("messageId", "msg_" + System.currentTimeMillis());
        });

        worker.register("report.generate", ctx -> {
            var reportId = ctx.job().argsMap().get("id");
            System.out.println("Generating report: " + reportId);
            // Long-running job -- send a heartbeat to extend the visibility timeout
            ctx.heartbeat();
            Thread.sleep(500);
            return Map.of("url", "https://reports.example.com/" + reportId);
        });

        // --- Add logging middleware ---

        worker.use((ctx, next) -> {
            System.out.printf("[%s] Processing %s (attempt %d)%n",
                    Instant.now(), ctx.job().type(), ctx.attempt());
            var start = Instant.now();
            next.handle(ctx);
            System.out.printf("[%s] Done %s in %dms%n",
                    Instant.now(), ctx.job().type(),
                    Duration.between(start, Instant.now()).toMillis());
        });

        // --- Add error recovery middleware ---

        worker.use((ctx, next) -> {
            try {
                next.handle(ctx);
            } catch (Exception e) {
                System.err.printf("Job %s failed: %s%n", ctx.job().id(), e.getMessage());
                throw e;
            }
        });

        System.out.println("Starting worker " + worker.getWorkerId());

        // Register a JVM shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down worker...");
            worker.stop();
        }));

        // start() blocks until stop() is called
        worker.start();
    }
}
