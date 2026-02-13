package org.openjobspec.ojs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OJSClient} static parsing methods, {@link JobRequest} building,
 * and the builder patterns for {@link RetryPolicy} and {@link UniquePolicy}.
 *
 * <p>These tests exercise parsing and request construction logic without requiring
 * an HTTP connection to an OJS server.
 */
class OJSClientTest {

    // -----------------------------------------------------------------------
    // Job Parsing
    // -----------------------------------------------------------------------

    @Nested
    class ParseJobTests {

        @Test
        void parseMinimalJobResponse() {
            var response = Map.<String, Object>of(
                    "job", Map.<String, Object>of(
                            "id", "01912f4e-fd1a-7000-8000-000000000001",
                            "type", "email.send",
                            "state", "available"
                    )
            );

            Job job = OJSClient.parseJob(response);

            assertEquals("01912f4e-fd1a-7000-8000-000000000001", job.id());
            assertEquals("email.send", job.type());
            assertEquals("available", job.state());
            assertEquals(Job.SPEC_VERSION, job.specversion());
            assertEquals("default", job.queue());
            assertEquals(0, job.priority());
            assertEquals(0, job.timeout());
            assertEquals(0, job.attempt());
            assertNull(job.scheduledAt());
            assertNull(job.expiresAt());
            assertNull(job.createdAt());
            assertNull(job.enqueuedAt());
            assertNull(job.startedAt());
            assertNull(job.completedAt());
            assertNull(job.retry());
            assertNull(job.unique());
            assertNull(job.schema());
            assertNull(job.error());
            assertNull(job.result());
            assertTrue(job.args().isEmpty());
            assertTrue(job.meta().isEmpty());
            assertTrue(job.tags().isEmpty());
        }

        @Test
        void parseFullJobResponse() {
            var retryMap = Map.<String, Object>of(
                    "max_attempts", 5,
                    "initial_interval_ms", 2000,
                    "backoff_coefficient", 3.0,
                    "max_interval_ms", 600000,
                    "jitter", false
            );

            var argsList = List.<Object>of(Map.<String, Object>of("to", "user@example.com", "subject", "Hello"));
            var metaMap = Map.<String, Object>of("source", "api", "correlation_id", "abc-123");
            var tagsList = List.<Object>of("urgent", "transactional");

            var jobMap = new HashMap<String, Object>();
            jobMap.put("specversion", "1.0.0-rc.1");
            jobMap.put("id", "01912f4e-fd1a-7000-8000-000000000002");
            jobMap.put("type", "email.send");
            jobMap.put("queue", "emails");
            jobMap.put("args", argsList);
            jobMap.put("meta", metaMap);
            jobMap.put("priority", 10);
            jobMap.put("timeout", 30000);
            jobMap.put("scheduled_at", "2025-01-15T10:00:00Z");
            jobMap.put("expires_at", "2025-01-16T10:00:00Z");
            jobMap.put("retry", retryMap);
            jobMap.put("schema", "https://example.com/schemas/email.json");
            jobMap.put("state", "active");
            jobMap.put("attempt", 2);
            jobMap.put("created_at", "2025-01-15T09:00:00Z");
            jobMap.put("enqueued_at", "2025-01-15T09:00:01Z");
            jobMap.put("started_at", "2025-01-15T10:00:00Z");
            jobMap.put("completed_at", null);
            jobMap.put("result", Map.of("delivered", true));
            jobMap.put("tags", tagsList);

            var response = Map.<String, Object>of("job", jobMap);

            Job job = OJSClient.parseJob(response);

            assertEquals("1.0.0-rc.1", job.specversion());
            assertEquals("01912f4e-fd1a-7000-8000-000000000002", job.id());
            assertEquals("email.send", job.type());
            assertEquals("emails", job.queue());
            assertEquals(1, job.args().size());
            assertEquals("api", job.meta().get("source"));
            assertEquals("abc-123", job.meta().get("correlation_id"));
            assertEquals(10, job.priority());
            assertEquals(30000, job.timeout());
            assertEquals("2025-01-15T10:00:00Z", job.scheduledAt());
            assertEquals("2025-01-16T10:00:00Z", job.expiresAt());
            assertEquals("https://example.com/schemas/email.json", job.schema());
            assertEquals("active", job.state());
            assertEquals(2, job.attempt());
            assertEquals("2025-01-15T09:00:00Z", job.createdAt());
            assertEquals("2025-01-15T09:00:01Z", job.enqueuedAt());
            assertEquals("2025-01-15T10:00:00Z", job.startedAt());
            assertNull(job.completedAt());
            assertNull(job.error());
            assertNotNull(job.result());
            assertEquals(List.of("urgent", "transactional"), job.tags());

            // Verify retry policy was parsed
            assertNotNull(job.retry());
            assertEquals(5, job.retry().maxAttempts());
            assertEquals(Duration.ofMillis(2000), job.retry().initialInterval());
            assertEquals(3.0, job.retry().backoffCoefficient());
            assertEquals(Duration.ofMillis(600000), job.retry().maxInterval());
            assertFalse(job.retry().jitter());
        }

        @Test
        void parseJobWithoutJobWrapper() {
            // parseJob should handle responses where the job data is at the top level
            var response = new HashMap<String, Object>();
            response.put("id", "01912f4e-fd1a-7000-8000-000000000003");
            response.put("type", "report.generate");
            response.put("state", "completed");
            response.put("completed_at", "2025-01-15T11:00:00Z");
            response.put("result", "success");

            Job job = OJSClient.parseJob(response);

            assertEquals("01912f4e-fd1a-7000-8000-000000000003", job.id());
            assertEquals("report.generate", job.type());
            assertEquals("completed", job.state());
            assertEquals("2025-01-15T11:00:00Z", job.completedAt());
            assertEquals("success", job.result());
        }

        @Test
        void parseJobWithErrorInformation() {
            var errorMap = Map.<String, Object>of(
                    "type", "RuntimeError",
                    "message", "Connection refused to smtp.example.com:587",
                    "backtrace", List.of(
                            "at EmailService.send(EmailService.java:42)",
                            "at Worker.execute(Worker.java:100)"
                    ),
                    "code", "SMTP_CONNECTION_FAILED",
                    "details", Map.of("host", "smtp.example.com", "port", 587)
            );

            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "01912f4e-fd1a-7000-8000-000000000004");
            jobMap.put("type", "email.send");
            jobMap.put("state", "retryable");
            jobMap.put("attempt", 3);
            jobMap.put("error", errorMap);

            var response = Map.<String, Object>of("job", jobMap);

            Job job = OJSClient.parseJob(response);

            assertEquals("retryable", job.state());
            assertEquals(3, job.attempt());

            assertNotNull(job.error());
            assertEquals("RuntimeError", job.error().type());
            assertEquals("Connection refused to smtp.example.com:587", job.error().message());
            assertEquals("SMTP_CONNECTION_FAILED", job.error().code());
            assertEquals(2, job.error().backtrace().size());
            assertEquals("at EmailService.send(EmailService.java:42)", job.error().backtrace().get(0));
            assertEquals("at Worker.execute(Worker.java:100)", job.error().backtrace().get(1));
            assertNotNull(job.error().details());
            assertEquals("smtp.example.com", job.error().details().get("host"));
            assertEquals(587, job.error().details().get("port"));
        }

        @Test
        void parseJobWithCompletedState() {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "01912f4e-fd1a-7000-8000-000000000005");
            jobMap.put("type", "report.generate");
            jobMap.put("state", "completed");
            jobMap.put("attempt", 1);
            jobMap.put("created_at", "2025-01-15T09:00:00Z");
            jobMap.put("enqueued_at", "2025-01-15T09:00:01Z");
            jobMap.put("started_at", "2025-01-15T09:01:00Z");
            jobMap.put("completed_at", "2025-01-15T09:02:30Z");
            jobMap.put("result", Map.of("report_url", "https://example.com/reports/42.pdf"));

            var response = Map.<String, Object>of("job", jobMap);

            Job job = OJSClient.parseJob(response);

            assertEquals("completed", job.state());
            assertTrue(job.isTerminal());
            assertEquals(1, job.attempt());
            assertNotNull(job.completedAt());
            assertNotNull(job.result());
        }

        @Test
        void parseJobWithNullRetryReturnsNull() {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "01912f4e-fd1a-7000-8000-000000000006");
            jobMap.put("type", "simple.task");
            jobMap.put("state", "available");
            jobMap.put("retry", null);

            var response = Map.<String, Object>of("job", jobMap);

            Job job = OJSClient.parseJob(response);
            assertNull(job.retry());
        }

        @Test
        void parseJobWithEmptyArgs() {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "01912f4e-fd1a-7000-8000-000000000007");
            jobMap.put("type", "cleanup.run");
            jobMap.put("state", "available");
            jobMap.put("args", List.of());

            var response = Map.<String, Object>of("job", jobMap);

            Job job = OJSClient.parseJob(response);
            assertTrue(job.args().isEmpty());
        }

        @Test
        void parseJobDefaultsQueueToDefault() {
            var response = Map.<String, Object>of(
                    "job", Map.<String, Object>of(
                            "id", "01912f4e-fd1a-7000-8000-000000000008",
                            "type", "task.run",
                            "state", "available"
                    )
            );

            Job job = OJSClient.parseJob(response);
            assertEquals("default", job.queue());
        }

        @Test
        void parseJobDefaultsSpecversion() {
            var response = Map.<String, Object>of(
                    "job", Map.<String, Object>of(
                            "id", "01912f4e-fd1a-7000-8000-000000000009",
                            "type", "task.run",
                            "state", "available"
                    )
            );

            Job job = OJSClient.parseJob(response);
            assertEquals(Job.SPEC_VERSION, job.specversion());
        }
    }

    // -----------------------------------------------------------------------
    // Retry Policy Parsing
    // -----------------------------------------------------------------------

    @Nested
    class ParseRetryPolicyTests {

        @Test
        void parseFullRetryPolicy() {
            var retryMap = Map.<String, Object>of(
                    "max_attempts", 5,
                    "initial_interval_ms", 2000,
                    "backoff_coefficient", 1.5,
                    "max_interval_ms", 120000,
                    "jitter", false,
                    "non_retryable_errors", List.of("ValidationError", "AuthError")
            );

            RetryPolicy policy = OJSClient.parseRetryPolicy(retryMap);

            assertNotNull(policy);
            assertEquals(5, policy.maxAttempts());
            assertEquals(Duration.ofMillis(2000), policy.initialInterval());
            assertEquals(1.5, policy.backoffCoefficient());
            assertEquals(Duration.ofMillis(120000), policy.maxInterval());
            assertFalse(policy.jitter());
            assertEquals(List.of("ValidationError", "AuthError"), policy.nonRetryableErrors());
        }

        @Test
        void parseRetryPolicyWithDefaults() {
            // A map with no keys should still produce a policy with default values
            var retryMap = Map.<String, Object>of();

            RetryPolicy policy = OJSClient.parseRetryPolicy(retryMap);

            assertNotNull(policy);
            assertEquals(3, policy.maxAttempts());
            assertEquals(Duration.ofMillis(1000), policy.initialInterval());
            assertEquals(2.0, policy.backoffCoefficient());
            assertEquals(Duration.ofMillis(300000), policy.maxInterval());
            assertTrue(policy.jitter());
            assertTrue(policy.nonRetryableErrors().isEmpty());
        }

        @Test
        void parseRetryPolicyFromNullReturnsNull() {
            assertNull(OJSClient.parseRetryPolicy(null));
        }

        @Test
        void parseRetryPolicyFromNonMapReturnsNull() {
            assertNull(OJSClient.parseRetryPolicy("not a map"));
            assertNull(OJSClient.parseRetryPolicy(42));
            assertNull(OJSClient.parseRetryPolicy(List.of()));
        }

        @Test
        void parseRetryPolicyPartialOverride() {
            var retryMap = Map.<String, Object>of(
                    "max_attempts", 10,
                    "jitter", false
            );

            RetryPolicy policy = OJSClient.parseRetryPolicy(retryMap);

            assertNotNull(policy);
            assertEquals(10, policy.maxAttempts());
            assertFalse(policy.jitter());
            // Other fields should have defaults
            assertEquals(Duration.ofMillis(1000), policy.initialInterval());
            assertEquals(2.0, policy.backoffCoefficient());
            assertEquals(Duration.ofMillis(300000), policy.maxInterval());
        }
    }

    // -----------------------------------------------------------------------
    // Job Error Parsing
    // -----------------------------------------------------------------------

    @Nested
    class ParseJobErrorTests {

        @Test
        void parseFullJobError() {
            var errorMap = Map.<String, Object>of(
                    "type", "TimeoutError",
                    "message", "Job execution exceeded 30s timeout",
                    "backtrace", List.of("at Handler.run(Handler.java:55)"),
                    "code", "TIMEOUT",
                    "details", Map.of("timeout_ms", 30000, "elapsed_ms", 30125)
            );

            Job.JobError error = OJSClient.parseJobError(errorMap);

            assertNotNull(error);
            assertEquals("TimeoutError", error.type());
            assertEquals("Job execution exceeded 30s timeout", error.message());
            assertEquals(1, error.backtrace().size());
            assertEquals("at Handler.run(Handler.java:55)", error.backtrace().get(0));
            assertEquals("TIMEOUT", error.code());
            assertNotNull(error.details());
            assertEquals(30000, error.details().get("timeout_ms"));
            assertEquals(30125, error.details().get("elapsed_ms"));
        }

        @Test
        void parseJobErrorWithMinimalFields() {
            var errorMap = Map.<String, Object>of(
                    "message", "Something went wrong"
            );

            Job.JobError error = OJSClient.parseJobError(errorMap);

            assertNotNull(error);
            assertEquals("unknown", error.type());
            assertEquals("Something went wrong", error.message());
            assertTrue(error.backtrace().isEmpty());
            assertNull(error.code());
            assertTrue(error.details().isEmpty());
        }

        @Test
        void parseJobErrorFromNullReturnsNull() {
            assertNull(OJSClient.parseJobError(null));
        }

        @Test
        void parseJobErrorFromNonMapReturnsNull() {
            assertNull(OJSClient.parseJobError("not a map"));
            assertNull(OJSClient.parseJobError(123));
            assertNull(OJSClient.parseJobError(List.of("error")));
        }

        @Test
        void parseJobErrorWithEmptyMap() {
            var errorMap = Map.<String, Object>of();

            Job.JobError error = OJSClient.parseJobError(errorMap);

            assertNotNull(error);
            assertEquals("unknown", error.type());
            assertEquals("", error.message());
            assertTrue(error.backtrace().isEmpty());
            assertNull(error.code());
        }
    }

    // -----------------------------------------------------------------------
    // JobRequest Building
    // -----------------------------------------------------------------------

    @Nested
    class BuildRequestTests {

        @Test
        void buildMinimalRequest() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("task.run", (Object) Map.of("key", "value"));

            Map<String, Object> wire = request.buildRequest();

            assertEquals("task.run", wire.get("type"));

            @SuppressWarnings("unchecked")
            var args = (List<Object>) wire.get("args");
            assertNotNull(args);
            assertEquals(1, args.size());

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertNotNull(options);
            assertEquals("default", options.get("queue"));
            assertFalse(options.containsKey("priority"));
            assertFalse(options.containsKey("timeout_ms"));
            assertFalse(options.containsKey("delay_until"));
            assertFalse(options.containsKey("expires_at"));
            assertFalse(options.containsKey("retry"));
            assertFalse(options.containsKey("unique"));
            assertFalse(options.containsKey("tags"));
        }

        @Test
        void buildRequestWithQueueAndPriority() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("email.send", (Object) Map.of("to", "user@test.com"))
                    .queue("emails")
                    .priority(10);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertEquals("emails", options.get("queue"));
            assertEquals(10, options.get("priority"));
        }

        @Test
        void buildRequestWithTimeout() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("long.task", (Object) Map.of())
                    .timeout(Duration.ofSeconds(120));

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertEquals(120000L, options.get("timeout_ms"));
        }

        @Test
        void buildRequestWithScheduledAt() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var scheduleTime = Instant.parse("2025-06-15T10:00:00Z");
            var request = client.enqueue("report.generate", (Object) Map.of("id", 42))
                    .scheduledAt(scheduleTime);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertEquals(scheduleTime.toString(), options.get("delay_until"));
        }

        @Test
        void buildRequestWithExpiresAt() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var expiryTime = Instant.parse("2025-06-16T10:00:00Z");
            var request = client.enqueue("task.run", (Object) Map.of())
                    .expiresAt(expiryTime);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertEquals(expiryTime.toString(), options.get("expires_at"));
        }

        @Test
        void buildRequestWithRetryPolicy() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var retryPolicy = RetryPolicy.builder()
                    .maxAttempts(5)
                    .initialInterval(Duration.ofSeconds(2))
                    .backoffCoefficient(3.0)
                    .maxInterval(Duration.ofMinutes(10))
                    .jitter(false)
                    .nonRetryableErrors(List.of("ValidationError"))
                    .build();

            var request = client.enqueue("task.run", (Object) Map.of())
                    .retry(retryPolicy);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertTrue(options.containsKey("retry"));

            @SuppressWarnings("unchecked")
            var retryWire = (Map<String, Object>) options.get("retry");
            assertEquals(5, retryWire.get("max_attempts"));
            assertEquals(2000L, retryWire.get("initial_interval_ms"));
            assertEquals(3.0, retryWire.get("backoff_coefficient"));
            assertEquals(600000L, retryWire.get("max_interval_ms"));
            assertEquals(false, retryWire.get("jitter"));
            assertEquals(List.of("ValidationError"), retryWire.get("non_retryable_errors"));
        }

        @Test
        void buildRequestWithUniquePolicy() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var uniquePolicy = UniquePolicy.builder()
                    .key(List.of("type", "args"))
                    .period(Duration.ofHours(1))
                    .onConflict("replace")
                    .build();

            var request = client.enqueue("task.run", (Object) Map.of())
                    .unique(uniquePolicy);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertTrue(options.containsKey("unique"));

            @SuppressWarnings("unchecked")
            var uniqueWire = (Map<String, Object>) options.get("unique");
            assertEquals(List.of("type", "args"), uniqueWire.get("key"));
            assertEquals(3600000L, uniqueWire.get("period_ms"));
            assertEquals("replace", uniqueWire.get("on_conflict"));
        }

        @Test
        void buildRequestWithMeta() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var meta = Map.<String, Object>of("source", "test", "trace_id", "abc-123");
            var request = client.enqueue("task.run", (Object) Map.of())
                    .meta(meta);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var wireMeta = (Map<String, Object>) wire.get("meta");
            assertNotNull(wireMeta);
            assertEquals("test", wireMeta.get("source"));
            assertEquals("abc-123", wireMeta.get("trace_id"));
        }

        @Test
        void buildRequestWithTags() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("task.run", (Object) Map.of())
                    .tags(List.of("urgent", "transactional"));

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertEquals(List.of("urgent", "transactional"), options.get("tags"));
        }

        @Test
        void buildRequestWithSchema() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("task.run", (Object) Map.of())
                    .schema("https://example.com/schemas/task.json");

            Map<String, Object> wire = request.buildRequest();

            assertEquals("https://example.com/schemas/task.json", wire.get("schema"));
        }

        @Test
        void buildRequestWithAllOptions() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var scheduleTime = Instant.parse("2025-06-15T12:00:00Z");
            var expiryTime = Instant.parse("2025-06-16T12:00:00Z");

            var request = client.enqueue("email.send", (Object) Map.of("to", "user@test.com"))
                    .queue("high-priority")
                    .priority(100)
                    .timeout(Duration.ofMinutes(5))
                    .scheduledAt(scheduleTime)
                    .expiresAt(expiryTime)
                    .retry(RetryPolicy.builder().maxAttempts(10).build())
                    .unique(UniquePolicy.builder().key(List.of("args")).period(Duration.ofHours(2)).build())
                    .meta(Map.of("origin", "test"))
                    .tags(List.of("critical"))
                    .schema("https://example.com/schemas/email.json");

            Map<String, Object> wire = request.buildRequest();

            assertEquals("email.send", wire.get("type"));
            assertEquals("https://example.com/schemas/email.json", wire.get("schema"));

            @SuppressWarnings("unchecked")
            var meta = (Map<String, Object>) wire.get("meta");
            assertEquals("test", meta.get("origin"));

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertEquals("high-priority", options.get("queue"));
            assertEquals(100, options.get("priority"));
            assertEquals(300000L, options.get("timeout_ms"));
            assertEquals(scheduleTime.toString(), options.get("delay_until"));
            assertEquals(expiryTime.toString(), options.get("expires_at"));
            assertTrue(options.containsKey("retry"));
            assertTrue(options.containsKey("unique"));
            assertEquals(List.of("critical"), options.get("tags"));
        }

        @Test
        void buildRequestOmitsEmptyMeta() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("task.run", (Object) Map.of());

            Map<String, Object> wire = request.buildRequest();

            assertFalse(wire.containsKey("meta"));
        }

        @Test
        void buildRequestOmitsEmptyTags() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("task.run", (Object) Map.of());

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertFalse(options.containsKey("tags"));
        }

        @Test
        void buildRequestOmitsZeroPriority() {
            var client = OJSClient.builder().url("http://localhost:8080").build();
            var request = client.enqueue("task.run", (Object) Map.of())
                    .priority(0);

            Map<String, Object> wire = request.buildRequest();

            @SuppressWarnings("unchecked")
            var options = (Map<String, Object>) wire.get("options");
            assertFalse(options.containsKey("priority"));
        }
    }

    // -----------------------------------------------------------------------
    // RetryPolicy Builder and Defaults
    // -----------------------------------------------------------------------

    @Nested
    class RetryPolicyTests {

        @Test
        void builderDefaults() {
            RetryPolicy policy = RetryPolicy.builder().build();

            assertEquals(3, policy.maxAttempts());
            assertEquals(Duration.ofSeconds(1), policy.initialInterval());
            assertEquals(2.0, policy.backoffCoefficient());
            assertEquals(Duration.ofMinutes(5), policy.maxInterval());
            assertTrue(policy.jitter());
            assertTrue(policy.nonRetryableErrors().isEmpty());
            assertEquals("discard", policy.onExhaustion());
        }

        @Test
        void builderCustomValues() {
            RetryPolicy policy = RetryPolicy.builder()
                    .maxAttempts(10)
                    .initialInterval(Duration.ofSeconds(5))
                    .backoffCoefficient(4.0)
                    .maxInterval(Duration.ofHours(1))
                    .jitter(false)
                    .nonRetryableErrors(List.of("FatalError", "AuthError"))
                    .onExhaustion("dead_letter")
                    .build();

            assertEquals(10, policy.maxAttempts());
            assertEquals(Duration.ofSeconds(5), policy.initialInterval());
            assertEquals(4.0, policy.backoffCoefficient());
            assertEquals(Duration.ofHours(1), policy.maxInterval());
            assertFalse(policy.jitter());
            assertEquals(List.of("FatalError", "AuthError"), policy.nonRetryableErrors());
            assertEquals("dead_letter", policy.onExhaustion());
        }

        @Test
        void defaultConstantMatchesBuilderDefaults() {
            RetryPolicy fromDefault = RetryPolicy.DEFAULT;
            RetryPolicy fromBuilder = RetryPolicy.builder().build();

            assertEquals(fromDefault.maxAttempts(), fromBuilder.maxAttempts());
            assertEquals(fromDefault.initialInterval(), fromBuilder.initialInterval());
            assertEquals(fromDefault.backoffCoefficient(), fromBuilder.backoffCoefficient());
            assertEquals(fromDefault.maxInterval(), fromBuilder.maxInterval());
            assertEquals(fromDefault.jitter(), fromBuilder.jitter());
            assertEquals(fromDefault.nonRetryableErrors(), fromBuilder.nonRetryableErrors());
            assertEquals(fromDefault.onExhaustion(), fromBuilder.onExhaustion());
        }

        @Test
        void invalidMaxAttemptsThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    RetryPolicy.builder().maxAttempts(-1).build()
            );
        }

        @Test
        void invalidBackoffCoefficientThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    RetryPolicy.builder().backoffCoefficient(0.5).build()
            );
        }

        @Test
        void zeroMaxAttemptsIsAllowed() {
            RetryPolicy policy = RetryPolicy.builder().maxAttempts(0).build();
            assertEquals(0, policy.maxAttempts());
        }

        @Test
        void backoffCoefficientOfOneIsAllowed() {
            RetryPolicy policy = RetryPolicy.builder().backoffCoefficient(1.0).build();
            assertEquals(1.0, policy.backoffCoefficient());
        }

        @Test
        void retryPolicyWireSerialization() {
            var policy = RetryPolicy.builder()
                    .maxAttempts(7)
                    .initialInterval(Duration.ofMillis(500))
                    .backoffCoefficient(2.5)
                    .maxInterval(Duration.ofMinutes(15))
                    .jitter(true)
                    .nonRetryableErrors(List.of("PermanentError"))
                    .build();

            var wire = JobRequest.buildRetryWire(policy);

            assertEquals(7, wire.get("max_attempts"));
            assertEquals(500L, wire.get("initial_interval_ms"));
            assertEquals(2.5, wire.get("backoff_coefficient"));
            assertEquals(900000L, wire.get("max_interval_ms"));
            assertEquals(true, wire.get("jitter"));
            assertEquals(List.of("PermanentError"), wire.get("non_retryable_errors"));
        }

        @Test
        void retryPolicyWireOmitsEmptyNonRetryableErrors() {
            var policy = RetryPolicy.builder().build();

            var wire = JobRequest.buildRetryWire(policy);

            assertFalse(wire.containsKey("non_retryable_errors"));
        }
    }

    // -----------------------------------------------------------------------
    // UniquePolicy Builder and Defaults
    // -----------------------------------------------------------------------

    @Nested
    class UniquePolicyTests {

        @Test
        void builderDefaults() {
            UniquePolicy policy = UniquePolicy.builder().build();

            assertTrue(policy.key().isEmpty());
            assertNull(policy.period());
            assertEquals("reject", policy.onConflict());
            assertEquals(UniquePolicy.DEFAULT_STATES, policy.states());
        }

        @Test
        void builderCustomValues() {
            UniquePolicy policy = UniquePolicy.builder()
                    .key(List.of("type", "queue", "args"))
                    .period(Duration.ofMinutes(30))
                    .onConflict("replace")
                    .states(List.of("available", "active"))
                    .build();

            assertEquals(List.of("type", "queue", "args"), policy.key());
            assertEquals(Duration.ofMinutes(30), policy.period());
            assertEquals("replace", policy.onConflict());
            assertEquals(List.of("available", "active"), policy.states());
        }

        @Test
        void defaultStatesIncludesExpectedValues() {
            var defaultStates = UniquePolicy.DEFAULT_STATES;

            assertTrue(defaultStates.contains("available"));
            assertTrue(defaultStates.contains("active"));
            assertTrue(defaultStates.contains("scheduled"));
            assertTrue(defaultStates.contains("retryable"));
            assertTrue(defaultStates.contains("pending"));
            assertEquals(5, defaultStates.size());
        }

        @Test
        void uniquePolicyWireSerialization() {
            var policy = UniquePolicy.builder()
                    .key(List.of("type", "args"))
                    .period(Duration.ofHours(2))
                    .onConflict("update")
                    .build();

            var wire = JobRequest.buildUniqueWire(policy);

            assertEquals(List.of("type", "args"), wire.get("key"));
            assertEquals(7200000L, wire.get("period_ms"));
            assertEquals("update", wire.get("on_conflict"));
        }

        @Test
        void uniquePolicyWireOmitsEmptyKey() {
            var policy = UniquePolicy.builder()
                    .onConflict("reject")
                    .build();

            var wire = JobRequest.buildUniqueWire(policy);

            assertFalse(wire.containsKey("key"));
            assertEquals("reject", wire.get("on_conflict"));
        }

        @Test
        void uniquePolicyWireOmitsNullPeriod() {
            var policy = UniquePolicy.builder()
                    .key(List.of("type"))
                    .build();

            var wire = JobRequest.buildUniqueWire(policy);

            assertFalse(wire.containsKey("period_ms"));
        }
    }

    // -----------------------------------------------------------------------
    // Job Record
    // -----------------------------------------------------------------------

    @Nested
    class JobRecordTests {

        @Test
        void jobRequiresType() {
            assertThrows(NullPointerException.class, () ->
                    new Job(null, null, null, null, null, null, 0, 0,
                            null, null, null, null, null, null, 0,
                            null, null, null, null, null, null, null)
            );
        }

        @Test
        void jobDefaultValues() {
            var job = new Job(null, "id-1", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, null, 0,
                    null, null, null, null, null, null, null);

            assertEquals(Job.SPEC_VERSION, job.specversion());
            assertEquals("default", job.queue());
            assertTrue(job.args().isEmpty());
            assertTrue(job.meta().isEmpty());
            assertTrue(job.tags().isEmpty());
        }

        @Test
        void terminalStates() {
            var completedJob = new Job(null, "id-1", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, "completed", 0,
                    null, null, null, null, null, null, null);
            assertTrue(completedJob.isTerminal());

            var cancelledJob = new Job(null, "id-2", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, "cancelled", 0,
                    null, null, null, null, null, null, null);
            assertTrue(cancelledJob.isTerminal());

            var discardedJob = new Job(null, "id-3", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, "discarded", 0,
                    null, null, null, null, null, null, null);
            assertTrue(discardedJob.isTerminal());
        }

        @Test
        void nonTerminalStates() {
            var activeJob = new Job(null, "id-1", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, "active", 0,
                    null, null, null, null, null, null, null);
            assertFalse(activeJob.isTerminal());

            var availableJob = new Job(null, "id-2", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, "available", 0,
                    null, null, null, null, null, null, null);
            assertFalse(availableJob.isTerminal());
        }

        @Test
        void nullStateIsNotTerminal() {
            var job = new Job(null, "id-1", "test.job", null, null, null, 0, 0,
                    null, null, null, null, null, null, 0,
                    null, null, null, null, null, null, null);
            assertFalse(job.isTerminal());
        }

        @Test
        void argsMapWithSingleMapElement() {
            var argsValue = Map.<String, Object>of("key", "value", "count", 42);
            var job = new Job(null, "id-1", "test.job", null,
                    List.of(argsValue), null, 0, 0,
                    null, null, null, null, null, null, 0,
                    null, null, null, null, null, null, null);

            assertEquals(argsValue, job.argsMap());
        }

        @Test
        void argsMapWithEmptyArgs() {
            var job = new Job(null, "id-1", "test.job", null,
                    List.of(), null, 0, 0,
                    null, null, null, null, null, null, 0,
                    null, null, null, null, null, null, null);

            assertTrue(job.argsMap().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Job.JobError Record
    // -----------------------------------------------------------------------

    @Nested
    class JobErrorRecordTests {

        @Test
        void jobErrorDefaults() {
            var error = new Job.JobError(null, null, null, null, null);

            assertEquals("unknown", error.type());
            assertEquals("", error.message());
            assertTrue(error.backtrace().isEmpty());
            assertNull(error.code());
            assertNull(error.details());
        }

        @Test
        void jobErrorWithAllFields() {
            var details = Map.<String, Object>of("retry_count", 3);
            var error = new Job.JobError(
                    "NetworkError",
                    "Connection timed out",
                    List.of("frame1", "frame2"),
                    "NET_TIMEOUT",
                    details
            );

            assertEquals("NetworkError", error.type());
            assertEquals("Connection timed out", error.message());
            assertEquals(List.of("frame1", "frame2"), error.backtrace());
            assertEquals("NET_TIMEOUT", error.code());
            assertEquals(details, error.details());
        }
    }

    // -----------------------------------------------------------------------
    // OJSClient Builder
    // -----------------------------------------------------------------------

    @Nested
    class ClientBuilderTests {

        @Test
        void builderRequiresUrl() {
            assertThrows(NullPointerException.class, () ->
                    OJSClient.builder().build()
            );
        }

        @Test
        void builderWithUrlSucceeds() {
            var client = OJSClient.builder()
                    .url("http://localhost:8080")
                    .build();
            assertNotNull(client);
        }

        @Test
        void builderWithAllOptions() {
            var client = OJSClient.builder()
                    .url("http://localhost:8080")
                    .authToken("my-secret-token")
                    .headers(Map.of("X-Custom", "header-value"))
                    .build();
            assertNotNull(client);
        }
    }

    // -----------------------------------------------------------------------
    // Job State Enum
    // -----------------------------------------------------------------------

    @Nested
    class JobStateTests {

        @Test
        void fromStringMapsAllStates() {
            assertEquals(Job.State.SCHEDULED, Job.State.fromString("scheduled"));
            assertEquals(Job.State.AVAILABLE, Job.State.fromString("available"));
            assertEquals(Job.State.PENDING, Job.State.fromString("pending"));
            assertEquals(Job.State.ACTIVE, Job.State.fromString("active"));
            assertEquals(Job.State.COMPLETED, Job.State.fromString("completed"));
            assertEquals(Job.State.RETRYABLE, Job.State.fromString("retryable"));
            assertEquals(Job.State.CANCELLED, Job.State.fromString("cancelled"));
            assertEquals(Job.State.DISCARDED, Job.State.fromString("discarded"));
        }

        @Test
        void fromStringThrowsForUnknownState() {
            assertThrows(IllegalArgumentException.class, () ->
                    Job.State.fromString("nonexistent")
            );
        }

        @Test
        void terminalStatesAreCorrect() {
            assertTrue(Job.State.COMPLETED.isTerminal());
            assertTrue(Job.State.CANCELLED.isTerminal());
            assertTrue(Job.State.DISCARDED.isTerminal());

            assertFalse(Job.State.SCHEDULED.isTerminal());
            assertFalse(Job.State.AVAILABLE.isTerminal());
            assertFalse(Job.State.PENDING.isTerminal());
            assertFalse(Job.State.ACTIVE.isTerminal());
            assertFalse(Job.State.RETRYABLE.isTerminal());
        }

        @Test
        void stateValues() {
            assertEquals("scheduled", Job.State.SCHEDULED.value());
            assertEquals("available", Job.State.AVAILABLE.value());
            assertEquals("pending", Job.State.PENDING.value());
            assertEquals("active", Job.State.ACTIVE.value());
            assertEquals("completed", Job.State.COMPLETED.value());
            assertEquals("retryable", Job.State.RETRYABLE.value());
            assertEquals("cancelled", Job.State.CANCELLED.value());
            assertEquals("discarded", Job.State.DISCARDED.value());
        }
    }
}
