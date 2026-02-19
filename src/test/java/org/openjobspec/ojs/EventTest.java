package org.openjobspec.ojs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void eventConstants() {
        assertEquals("job.enqueued", Event.JOB_ENQUEUED);
        assertEquals("job.completed", Event.JOB_COMPLETED);
        assertEquals("job.failed", Event.JOB_FAILED);
        assertEquals("worker.started", Event.WORKER_STARTED);
        assertEquals("workflow.completed", Event.WORKFLOW_COMPLETED);
        assertEquals("cron.triggered", Event.CRON_TRIGGERED);
        assertEquals("queue.paused", Event.QUEUE_PAUSED);
    }

    @Test
    void eventOfWithSubject() {
        Event e = Event.of(Event.JOB_COMPLETED, "job-123", Map.of("queue", "default"));
        assertEquals(Event.JOB_COMPLETED, e.type());
        assertEquals("job-123", e.subject());
        assertEquals("default", e.data().get("queue"));
        assertNotNull(e.time());
    }

    @Test
    void eventOfWithoutSubject() {
        Event e = Event.of(Event.WORKER_STARTED, Map.of("worker_id", "w1"));
        assertEquals(Event.WORKER_STARTED, e.type());
        assertNull(e.subject());
        assertEquals("w1", e.data().get("worker_id"));
    }

    @Test
    void fromMap() {
        Map<String, Object> wire = Map.of(
                "id", "evt-1",
                "type", "job.completed",
                "source", "/ojs/v1",
                "subject", "job-456",
                "time", "2025-01-01T00:00:00Z",
                "data", Map.of("result", "ok")
        );
        Event e = Event.fromMap(wire);
        assertEquals("evt-1", e.id());
        assertEquals("job.completed", e.type());
        assertEquals("/ojs/v1", e.source());
        assertEquals("job-456", e.subject());
        assertEquals("ok", e.data().get("result"));
    }

    @Test
    void fromMapMissingData() {
        Event e = Event.fromMap(Map.of("type", "job.started"));
        assertEquals("job.started", e.type());
        assertTrue(e.data().isEmpty());
    }

    @Test
    void jobIdFromSubject() {
        Event e = Event.of(Event.JOB_COMPLETED, "job-789", Map.of());
        assertEquals("job-789", e.jobId());
    }

    @Test
    void jobIdFromData() {
        Event e = new Event(null, "job.completed", null, null, null,
                Map.of("job_id", "job-abc"));
        assertEquals("job-abc", e.jobId());
    }

    @Test
    void eventCategoryChecks() {
        assertTrue(Event.of(Event.JOB_COMPLETED, Map.of()).isJobEvent());
        assertFalse(Event.of(Event.JOB_COMPLETED, Map.of()).isWorkerEvent());
        assertTrue(Event.of(Event.WORKFLOW_COMPLETED, Map.of()).isWorkflowEvent());
        assertTrue(Event.of(Event.WORKER_STARTED, Map.of()).isWorkerEvent());
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () -> Event.of(null, Map.of()));
    }

    // ── EventEmitter tests ───────────────────────────────

    private EventEmitter emitter;

    @BeforeEach
    void setUp() {
        emitter = new EventEmitter();
    }

    @Test
    void typedListener() {
        List<Event> received = new ArrayList<>();
        emitter.on(Event.JOB_COMPLETED, received::add);

        emitter.emit(Event.of(Event.JOB_COMPLETED, "j1", Map.of()));
        emitter.emit(Event.of(Event.JOB_FAILED, "j2", Map.of()));

        assertEquals(1, received.size());
        assertEquals("j1", received.getFirst().subject());
    }

    @Test
    void wildcardListener() {
        AtomicInteger count = new AtomicInteger();
        emitter.onAny(e -> count.incrementAndGet());

        emitter.emit(Event.of(Event.JOB_COMPLETED, Map.of()));
        emitter.emit(Event.of(Event.JOB_FAILED, Map.of()));
        emitter.emit(Event.of(Event.WORKER_STARTED, Map.of()));

        assertEquals(3, count.get());
    }

    @Test
    void unsubscribe() {
        AtomicInteger count = new AtomicInteger();
        Runnable unsub = emitter.on(Event.JOB_COMPLETED, e -> count.incrementAndGet());

        emitter.emit(Event.of(Event.JOB_COMPLETED, Map.of()));
        assertEquals(1, count.get());

        unsub.run();
        emitter.emit(Event.of(Event.JOB_COMPLETED, Map.of()));
        assertEquals(1, count.get());
    }

    @Test
    void unsubscribeWildcard() {
        AtomicInteger count = new AtomicInteger();
        Runnable unsub = emitter.onAny(e -> count.incrementAndGet());

        emitter.emit(Event.of(Event.JOB_COMPLETED, Map.of()));
        assertEquals(1, count.get());

        unsub.run();
        emitter.emit(Event.of(Event.JOB_COMPLETED, Map.of()));
        assertEquals(1, count.get());
    }

    @Test
    void clearRemovesAll() {
        emitter.on(Event.JOB_COMPLETED, e -> {});
        emitter.onAny(e -> {});
        assertEquals(2, emitter.listenerCount(Event.JOB_COMPLETED));

        emitter.clear();
        assertEquals(0, emitter.totalListeners());
    }

    @Test
    void removeAllForType() {
        emitter.on(Event.JOB_COMPLETED, e -> {});
        emitter.on(Event.JOB_COMPLETED, e -> {});
        emitter.on(Event.JOB_FAILED, e -> {});

        emitter.removeAll(Event.JOB_COMPLETED);
        assertEquals(0, emitter.listenerCount(Event.JOB_COMPLETED));
        assertEquals(1, emitter.listenerCount(Event.JOB_FAILED));
    }

    @Test
    void listenerCounting() {
        emitter.on(Event.JOB_COMPLETED, e -> {});
        emitter.onAny(e -> {});
        assertEquals(2, emitter.listenerCount(Event.JOB_COMPLETED));
        assertEquals(1, emitter.listenerCount(Event.JOB_FAILED)); // wildcard only
        assertEquals(2, emitter.totalListeners());
    }
}
