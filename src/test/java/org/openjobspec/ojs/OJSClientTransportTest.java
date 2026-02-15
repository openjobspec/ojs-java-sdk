package org.openjobspec.ojs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjobspec.ojs.OJSError.OJSException;
import org.openjobspec.ojs.transport.Transport;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link OJSClient} operations against a mocked {@link Transport}.
 * Verifies HTTP contract (paths, request bodies) and error handling.
 */
@ExtendWith(MockitoExtension.class)
class OJSClientTransportTest {

    @Mock
    Transport transport;

    OJSClient client;

    @BeforeEach
    void setUp() {
        client = OJSClient.withTransport(transport);
    }

    // -----------------------------------------------------------------------
    // Enqueue
    // -----------------------------------------------------------------------

    @Nested
    class EnqueueTests {

        @Test
        void enqueuePostsToJobsEndpoint() {
            var jobResponse = jobResponse("job_1", "email.send", "available");
            when(transport.post(eq("/jobs"), any())).thenReturn(jobResponse);

            var job = client.enqueue("email.send", Map.of("to", "user@test.com"));

            verify(transport).post(eq("/jobs"), argThat(body -> {
                assertEquals("email.send", body.get("type"));
                assertNotNull(body.get("args"));
                assertNotNull(body.get("options"));
                return true;
            }));
            assertEquals("job_1", job.id());
            assertEquals("email.send", job.type());
        }

        @Test
        void enqueueReturnsJobWithAllParsedFields() {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "job_2");
            jobMap.put("type", "report.generate");
            jobMap.put("queue", "reports");
            jobMap.put("state", "scheduled");
            jobMap.put("attempt", 0);
            jobMap.put("priority", 5);
            jobMap.put("created_at", "2025-01-15T09:00:00Z");

            when(transport.post(eq("/jobs"), any()))
                    .thenReturn(Map.of("job", jobMap));

            var job = client.enqueue("report.generate", Map.of("id", 42));

            assertEquals("reports", job.queue());
            assertEquals("scheduled", job.state());
            assertEquals(5, job.priority());
            assertEquals("2025-01-15T09:00:00Z", job.createdAt());
        }

        @Test
        void enqueueThrowsOnTransportError() {
            when(transport.post(eq("/jobs"), any()))
                    .thenThrow(new OJSException(new OJSError.TransportError(
                            "transport_error", "Connection refused", null)));

            var ex = assertThrows(OJSException.class,
                    () -> client.enqueue("email.send", Map.of()));
            assertTrue(ex.isRetryable());
            assertEquals("transport_error", ex.code());
        }

        @Test
        void enqueueThrowsOnApiError() {
            when(transport.post(eq("/jobs"), any()))
                    .thenThrow(new OJSException(new OJSError.ApiError(
                            "invalid_request", "Type is required", false,
                            Map.of(), "req_123", 422)));

            var ex = assertThrows(OJSException.class,
                    () -> client.enqueue("email.send", Map.of("invalid", true)));
            assertFalse(ex.isRetryable());
            assertEquals("invalid_request", ex.code());
        }
    }

    // -----------------------------------------------------------------------
    // Enqueue with options (JobRequest)
    // -----------------------------------------------------------------------

    @Nested
    class EnqueueWithOptionsTests {

        @Test
        void enqueueWithOptionsIncludesAllFieldsInRequest() {
            var jobResponse = jobResponse("job_3", "task.run", "available");
            when(transport.post(eq("/jobs"), any())).thenReturn(jobResponse);

            var retryPolicy = RetryPolicy.builder().maxAttempts(5).build();
            client.enqueue("task.run", (Object) Map.of("key", "value"))
                    .queue("high-priority")
                    .priority(10)
                    .retry(retryPolicy)
                    .tags(List.of("urgent"))
                    .send();

            verify(transport).post(eq("/jobs"), argThat(body -> {
                @SuppressWarnings("unchecked")
                var options = (Map<String, Object>) body.get("options");
                assertEquals("high-priority", options.get("queue"));
                assertEquals(10, options.get("priority"));
                assertNotNull(options.get("retry"));
                assertEquals(List.of("urgent"), options.get("tags"));
                return true;
            }));
        }
    }

    // -----------------------------------------------------------------------
    // Batch Enqueue
    // -----------------------------------------------------------------------

    @Nested
    class BatchEnqueueTests {

        @Test
        void enqueueBatchPostsToBatchEndpoint() {
            var response = Map.<String, Object>of("jobs", List.of(
                    Map.<String, Object>of("id", "job_a", "type", "email.send", "state", "available"),
                    Map.<String, Object>of("id", "job_b", "type", "sms.send", "state", "available")
            ));
            when(transport.post(eq("/jobs/batch"), any())).thenReturn(response);

            var jobs = client.enqueueBatch(List.of(
                    Map.of("type", "email.send", "args", List.of()),
                    Map.of("type", "sms.send", "args", List.of())
            ));

            verify(transport).post(eq("/jobs/batch"), argThat(body -> {
                assertNotNull(body.get("jobs"));
                return true;
            }));
            assertEquals(2, jobs.size());
            assertEquals("job_a", jobs.get(0).id());
            assertEquals("job_b", jobs.get(1).id());
        }

        @Test
        void enqueueBatchReturnsEmptyListForNoJobs() {
            when(transport.post(eq("/jobs/batch"), any()))
                    .thenReturn(Map.of("jobs", List.of()));

            var jobs = client.enqueueBatch(List.of());
            assertTrue(jobs.isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Get Job
    // -----------------------------------------------------------------------

    @Nested
    class GetJobTests {

        @Test
        void getJobCallsCorrectEndpoint() {
            var response = jobResponse("job_123", "email.send", "active");
            when(transport.get("/jobs/job_123")).thenReturn(response);

            var job = client.getJob("job_123");

            verify(transport).get("/jobs/job_123");
            assertEquals("job_123", job.id());
            assertEquals("active", job.state());
        }

        @Test
        void getJobThrowsNotFound() {
            when(transport.get("/jobs/nonexistent"))
                    .thenThrow(new OJSException(new OJSError.ApiError(
                            "not_found", "Job not found", false,
                            Map.of(), null, 404)));

            var ex = assertThrows(OJSException.class,
                    () -> client.getJob("nonexistent"));
            assertTrue(ex.isNotFound());
        }
    }

    // -----------------------------------------------------------------------
    // Cancel Job
    // -----------------------------------------------------------------------

    @Nested
    class CancelJobTests {

        @Test
        void cancelJobCallsDeleteEndpoint() {
            var response = jobResponse("job_456", "email.send", "cancelled");
            when(transport.delete("/jobs/job_456")).thenReturn(response);

            var job = client.cancelJob("job_456");

            verify(transport).delete("/jobs/job_456");
            assertEquals("cancelled", job.state());
        }

        @Test
        void cancelJobThrowsOnConflict() {
            when(transport.delete("/jobs/job_789"))
                    .thenThrow(new OJSException(new OJSError.ApiError(
                            "conflict", "Job already completed", false,
                            Map.of(), null, 409)));

            var ex = assertThrows(OJSException.class,
                    () -> client.cancelJob("job_789"));
            assertTrue(ex.isConflict());
        }
    }

    // -----------------------------------------------------------------------
    // Health
    // -----------------------------------------------------------------------

    @Nested
    class HealthTests {

        @Test
        void healthCallsHealthEndpoint() {
            when(transport.get("/health"))
                    .thenReturn(Map.of("status", "ok"));

            var result = client.health();

            verify(transport).get("/health");
            assertEquals("ok", result.get("status"));
        }
    }

    // -----------------------------------------------------------------------
    // Manifest
    // -----------------------------------------------------------------------

    @Nested
    class ManifestTests {

        @Test
        void manifestCallsAbsoluteEndpoint() {
            when(transport.getAbsolute("/ojs/manifest"))
                    .thenReturn(Map.of("name", "ojs-backend-redis", "version", "0.1.0"));

            var result = client.manifest();

            verify(transport).getAbsolute("/ojs/manifest");
            assertEquals("ojs-backend-redis", result.get("name"));
        }
    }

    // -----------------------------------------------------------------------
    // Queue Management
    // -----------------------------------------------------------------------

    @Nested
    class QueueTests {

        @Test
        void listQueuesCallsCorrectEndpoint() {
            var queues = List.<Map<String, Object>>of(
                    Map.of("name", "default", "size", 10),
                    Map.of("name", "emails", "size", 5)
            );
            when(transport.get("/queues")).thenReturn(Map.of("queues", queues));

            var result = client.listQueues();

            assertEquals(2, result.size());
            assertEquals("default", result.get(0).get("name"));
        }

        @Test
        void pauseQueuePostsToCorrectEndpoint() {
            when(transport.post(eq("/queues/emails/pause"), any())).thenReturn(Map.of());

            client.pauseQueue("emails");

            verify(transport).post(eq("/queues/emails/pause"), eq(Map.of()));
        }

        @Test
        void resumeQueuePostsToCorrectEndpoint() {
            when(transport.post(eq("/queues/emails/resume"), any())).thenReturn(Map.of());

            client.resumeQueue("emails");

            verify(transport).post(eq("/queues/emails/resume"), eq(Map.of()));
        }
    }

    // -----------------------------------------------------------------------
    // Workflows
    // -----------------------------------------------------------------------

    @Nested
    class WorkflowTests {

        @Test
        void createWorkflowPostsToWorkflowsEndpoint() {
            var workflowResponse = new HashMap<String, Object>();
            workflowResponse.put("id", "wf_1");
            workflowResponse.put("name", "order-processing");
            workflowResponse.put("state", "active");
            workflowResponse.put("steps", List.of());
            when(transport.post(eq("/workflows"), any())).thenReturn(workflowResponse);

            var chain = Workflow.chain("order-processing",
                    Workflow.step("order.validate", Map.of()),
                    Workflow.step("payment.charge", Map.of()));

            var status = client.createWorkflow(chain);

            verify(transport).post(eq("/workflows"), argThat(body -> {
                assertEquals("order-processing", body.get("name"));
                assertNotNull(body.get("steps"));
                return true;
            }));
            assertEquals("wf_1", status.id());
        }

        @Test
        void getWorkflowCallsCorrectEndpoint() {
            var response = new HashMap<String, Object>();
            response.put("id", "wf_2");
            response.put("name", "export-pipeline");
            response.put("state", "completed");
            response.put("steps", List.of());
            when(transport.get("/workflows/wf_2")).thenReturn(response);

            var status = client.getWorkflow("wf_2");

            verify(transport).get("/workflows/wf_2");
            assertEquals("wf_2", status.id());
        }

        @Test
        void cancelWorkflowCallsDeleteEndpoint() {
            when(transport.delete("/workflows/wf_3")).thenReturn(Map.of());

            client.cancelWorkflow("wf_3");

            verify(transport).delete("/workflows/wf_3");
        }
    }

    // -----------------------------------------------------------------------
    // Dead Letter Queue
    // -----------------------------------------------------------------------

    @Nested
    class DeadLetterTests {

        @Test
        void listDeadLetterJobsBuildsCorrectPath() {
            var response = Map.<String, Object>of("jobs", List.of());
            when(transport.get(startsWith("/dead-letter"))).thenReturn(response);

            client.listDeadLetterJobs("emails", 10, 0);

            verify(transport).get("/dead-letter?limit=10&offset=0&queue=emails");
        }

        @Test
        void listDeadLetterJobsWithoutQueueOmitsParam() {
            var response = Map.<String, Object>of("jobs", List.of());
            when(transport.get(startsWith("/dead-letter"))).thenReturn(response);

            client.listDeadLetterJobs(null, 25, 5);

            verify(transport).get("/dead-letter?limit=25&offset=5");
        }

        @Test
        void retryDeadLetterJobCallsCorrectEndpoint() {
            var response = jobResponse("dlq_1", "failed.task", "available");
            when(transport.post(eq("/dead-letter/dlq_1/retry"), any())).thenReturn(response);

            var job = client.retryDeadLetterJob("dlq_1");

            verify(transport).post(eq("/dead-letter/dlq_1/retry"), eq(Map.of()));
            assertEquals("dlq_1", job.id());
        }

        @Test
        void discardDeadLetterJobCallsDeleteEndpoint() {
            when(transport.delete("/dead-letter/dlq_2")).thenReturn(Map.of());

            client.discardDeadLetterJob("dlq_2");

            verify(transport).delete("/dead-letter/dlq_2");
        }
    }

    // -----------------------------------------------------------------------
    // withTransport factory
    // -----------------------------------------------------------------------

    @Test
    void withTransportRejectsNull() {
        assertThrows(NullPointerException.class,
                () -> OJSClient.withTransport(null));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Map<String, Object> jobResponse(String id, String type, String state) {
        var jobMap = new HashMap<String, Object>();
        jobMap.put("id", id);
        jobMap.put("type", type);
        jobMap.put("state", state);
        return Map.of("job", jobMap);
    }
}
