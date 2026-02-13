package org.openjobspec.ojs.integration;

import org.junit.jupiter.api.*;
import org.openjobspec.ojs.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests against a running OJS server with a Redis backend.
 *
 * <p>These tests require a running OJS server at {@code http://localhost:8080}.
 * They are disabled by default and should be run manually or in CI with
 * the appropriate infrastructure.
 *
 * <p>Run with: {@code mvn verify -Dit.test=RedisBackendIT}
 */
@Disabled("Requires a running OJS server with Redis backend")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisBackendIT {

    static OJSClient client;
    static String testJobId;

    @BeforeAll
    static void setup() {
        client = OJSClient.builder()
                .url(System.getProperty("ojs.server.url", "http://localhost:8080"))
                .build();
    }

    @Test
    @Order(1)
    void healthCheck() {
        var health = client.health();
        assertNotNull(health);
        assertEquals("ok", health.get("status"));
    }

    @Test
    @Order(2)
    void enqueueSimpleJob() {
        var job = client.enqueue("integration.test", Map.of("key", "value"));
        assertNotNull(job);
        assertNotNull(job.id());
        assertEquals("integration.test", job.type());
        assertEquals("default", job.queue());
        testJobId = job.id();
    }

    @Test
    @Order(3)
    void getJob() {
        assertNotNull(testJobId, "enqueueSimpleJob must run first");
        var job = client.getJob(testJobId);
        assertNotNull(job);
        assertEquals(testJobId, job.id());
        assertEquals("integration.test", job.type());
    }

    @Test
    @Order(4)
    void enqueueWithOptions() {
        var job = client.enqueue("integration.scheduled", (Object) Map.of("id", 42))
                .queue("test-queue")
                .priority(5)
                .delay(Duration.ofMinutes(10))
                .retry(RetryPolicy.builder().maxAttempts(5).build())
                .send();

        assertNotNull(job);
        assertNotNull(job.id());
        assertEquals("integration.scheduled", job.type());
        assertEquals("test-queue", job.queue());
    }

    @Test
    @Order(5)
    void cancelJob() {
        // Create a job to cancel
        var job = client.enqueue("integration.cancel_me", Map.of("test", true));
        assertNotNull(job.id());

        // Cancel it
        var cancelled = client.cancelJob(job.id());
        assertNotNull(cancelled);
        assertEquals("cancelled", cancelled.state());
    }

    @Test
    @Order(6)
    void listQueues() {
        var queues = client.listQueues();
        assertNotNull(queues);
        assertFalse(queues.isEmpty());
    }

    @Test
    @Order(7)
    void chainWorkflow() {
        var chain = Workflow.chain("integration-chain",
                Workflow.step("integration.step1", Map.of("order", 1)),
                Workflow.step("integration.step2", Map.of("order", 2)),
                Workflow.step("integration.step3", Map.of("order", 3))
        );

        var result = client.createWorkflow(chain);
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.state());
        assertFalse(result.steps().isEmpty());
    }

    @Test
    @Order(8)
    void groupWorkflow() {
        var group = Workflow.group("integration-group",
                Workflow.step("integration.parallel1", Map.of("part", "a")),
                Workflow.step("integration.parallel2", Map.of("part", "b"))
        );

        var result = client.createWorkflow(group);
        assertNotNull(result);
        assertNotNull(result.id());
    }

    @Test
    @Order(9)
    void workerProcessesJob() throws InterruptedException {
        // Enqueue a job
        var job = client.enqueue("integration.worker_test",
                Map.of("message", "hello from integration test"));
        assertNotNull(job.id());

        // Create a worker that processes it
        var worker = OJSWorker.builder()
                .url(System.getProperty("ojs.server.url", "http://localhost:8080"))
                .queues(List.of("default"))
                .concurrency(1)
                .build();

        var processed = new java.util.concurrent.atomic.AtomicBoolean(false);

        worker.register("integration.worker_test", ctx -> {
            processed.set(true);
            return Map.of("processed", true);
        });

        // Start worker in a virtual thread, process one job, then stop
        var workerThread = Thread.startVirtualThread(worker::start);

        // Wait for processing
        Thread.sleep(5000);
        worker.stop();
        workerThread.join(10000);

        assertTrue(processed.get(), "Worker should have processed the job");

        // Verify the job completed
        var completedJob = client.getJob(job.id());
        assertEquals("completed", completedJob.state());
    }
}
