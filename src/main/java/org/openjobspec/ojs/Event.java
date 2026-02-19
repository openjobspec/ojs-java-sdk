package org.openjobspec.ojs;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an OJS event following the CloudEvents-inspired envelope.
 *
 * <p>Events are emitted by OJS servers on job state transitions and other
 * lifecycle milestones. This class models the wire format for consuming
 * events from SSE, WebSocket, or webhook endpoints.
 *
 * <p>Usage example:
 * <pre>{@code
 * Event event = Event.of(Event.JOB_COMPLETED, "job-123",
 *     Map.of("queue", "default", "type", "email.send"));
 * }</pre>
 *
 * @param id      unique event identifier
 * @param type    event type (e.g., "job.completed")
 * @param source  identifies the context in which the event happened
 * @param subject primary subject of the event (typically a job ID)
 * @param time    when the event occurred (ISO 8601)
 * @param data    event-specific payload
 */
public record Event(
        String id,
        String type,
        String source,
        String subject,
        String time,
        Map<String, Object> data
) {

    // ── Core job events (REQUIRED) ────────────────────────
    public static final String JOB_ENQUEUED  = "job.enqueued";
    public static final String JOB_STARTED   = "job.started";
    public static final String JOB_COMPLETED = "job.completed";
    public static final String JOB_FAILED    = "job.failed";
    public static final String JOB_DISCARDED = "job.discarded";

    // ── Extended job events ───────────────────────────────
    public static final String JOB_RETRYING  = "job.retrying";
    public static final String JOB_CANCELLED = "job.cancelled";
    public static final String JOB_SCHEDULED = "job.scheduled";
    public static final String JOB_EXPIRED   = "job.expired";
    public static final String JOB_PROGRESS  = "job.progress";

    // ── Worker events ─────────────────────────────────────
    public static final String WORKER_STARTED   = "worker.started";
    public static final String WORKER_STOPPED   = "worker.stopped";
    public static final String WORKER_QUIET     = "worker.quiet";
    public static final String WORKER_HEARTBEAT = "worker.heartbeat";

    // ── Workflow events ───────────────────────────────────
    public static final String WORKFLOW_STARTED        = "workflow.started";
    public static final String WORKFLOW_STEP_COMPLETED = "workflow.step_completed";
    public static final String WORKFLOW_COMPLETED      = "workflow.completed";
    public static final String WORKFLOW_FAILED         = "workflow.failed";

    // ── Cron events ───────────────────────────────────────
    public static final String CRON_TRIGGERED = "cron.triggered";
    public static final String CRON_SKIPPED   = "cron.skipped";

    // ── Queue events ──────────────────────────────────────
    public static final String QUEUE_PAUSED  = "queue.paused";
    public static final String QUEUE_RESUMED = "queue.resumed";

    public Event {
        Objects.requireNonNull(type, "event type must not be null");
        if (data == null) data = Map.of();
    }

    /**
     * Creates an event with the given type, subject, and data, using the current time.
     */
    public static Event of(String type, String subject, Map<String, Object> data) {
        return new Event(null, type, null, subject, Instant.now().toString(), data);
    }

    /**
     * Creates an event with just a type and data.
     */
    public static Event of(String type, Map<String, Object> data) {
        return new Event(null, type, null, null, Instant.now().toString(), data);
    }

    /**
     * Constructs an Event from a parsed JSON map (wire format).
     */
    @SuppressWarnings("unchecked")
    public static Event fromMap(Map<String, Object> map) {
        return new Event(
                (String) map.get("id"),
                (String) map.get("type"),
                (String) map.get("source"),
                (String) map.get("subject"),
                (String) map.get("time"),
                map.containsKey("data") ? (Map<String, Object>) map.get("data") : Map.of()
        );
    }

    /**
     * Returns the job ID from the subject field, or from data if subject is null.
     */
    public String jobId() {
        if (subject != null) return subject;
        Object id = data.get("job_id");
        return id != null ? id.toString() : null;
    }

    /**
     * Returns whether this is a job lifecycle event.
     */
    public boolean isJobEvent() {
        return type != null && type.startsWith("job.");
    }

    /**
     * Returns whether this is a workflow event.
     */
    public boolean isWorkflowEvent() {
        return type != null && type.startsWith("workflow.");
    }

    /**
     * Returns whether this is a worker event.
     */
    public boolean isWorkerEvent() {
        return type != null && type.startsWith("worker.");
    }
}
