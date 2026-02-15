package org.openjobspec.ojs.testing;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * OJS Testing Module — fake mode, assertions, and test utilities.
 *
 * <p>Implements the OJS Testing Specification (ojs-testing.md).
 *
 * <p>Usage:
 * <pre>{@code
 * OJSTesting testing = OJSTesting.fake();
 *
 * // ... code that enqueues jobs ...
 *
 * testing.assertEnqueued("email.send");
 * testing.assertEnqueued("email.send", Map.of("queue", "email"));
 *
 * testing.drain();
 * testing.assertCompleted("email.send");
 *
 * testing.restore();
 * }</pre>
 */
public class OJSTesting {

    /** A job recorded in fake mode. */
    public record FakeJob(
            String id,
            String type,
            String queue,
            List<Object> args,
            Map<String, Object> meta,
            String state,
            int attempt,
            Instant createdAt
    ) {
        public FakeJob withState(String newState) {
            return new FakeJob(id, type, queue, args, meta, newState, attempt, createdAt);
        }

        public FakeJob withAttempt(int newAttempt) {
            return new FakeJob(id, type, queue, args, meta, state, newAttempt, createdAt);
        }
    }

    private static volatile OJSTesting activeInstance;

    private final List<FakeJob> enqueued = new CopyOnWriteArrayList<>();
    private final List<FakeJob> performed = new CopyOnWriteArrayList<>();
    private final Map<String, Consumer<FakeJob>> handlers = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);

    private OJSTesting() {}

    /** Activate fake mode. Returns a testing instance for assertions. */
    public static OJSTesting fake() {
        OJSTesting instance = new OJSTesting();
        activeInstance = instance;
        return instance;
    }

    /** Get the active testing instance, or null if not in fake mode. */
    public static OJSTesting getActive() {
        return activeInstance;
    }

    /** Restore real mode and clear all state. */
    public void restore() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        enqueued.clear();
        performed.clear();
        handlers.clear();
    }

    /** Register a handler for drain execution. */
    public void registerHandler(String type, Consumer<FakeJob> handler) {
        handlers.put(type, handler);
    }

    /** Record a job enqueue. Called by OJSClient when in fake mode. */
    public FakeJob recordEnqueue(String type, List<Object> args, String queue, Map<String, Object> meta) {
        int id = nextId.incrementAndGet();
        FakeJob job = new FakeJob(
                String.format("fake-%06d", id),
                type,
                queue != null ? queue : "default",
                args != null ? args : List.of(),
                meta != null ? meta : Map.of(),
                "available",
                0,
                Instant.now()
        );
        enqueued.add(job);
        return job;
    }

    // --- Assertions ---

    /** Assert that at least one job of the given type was enqueued. */
    public void assertEnqueued(String type) {
        assertEnqueued(type, null);
    }

    /** Assert that at least one job matching criteria was enqueued. */
    public void assertEnqueued(String type, Map<String, Object> criteria) {
        List<FakeJob> matches = findMatching(enqueued, type, criteria);
        if (matches.isEmpty()) {
            Set<String> types = enqueued.stream().map(FakeJob::type).collect(Collectors.toSet());
            throw new AssertionError(String.format(
                    "Expected at least one enqueued job of type '%s', found none. Enqueued types: %s",
                    type, types));
        }
    }

    /** Assert that exactly count jobs of the given type were enqueued. */
    public void assertEnqueuedCount(String type, int count) {
        List<FakeJob> matches = findMatching(enqueued, type, null);
        if (matches.size() != count) {
            throw new AssertionError(String.format(
                    "Expected %d enqueued job(s) of type '%s', found %d", count, type, matches.size()));
        }
    }

    /** Assert that NO job of the given type was enqueued. */
    public void refuteEnqueued(String type) {
        List<FakeJob> matches = findMatching(enqueued, type, null);
        if (!matches.isEmpty()) {
            throw new AssertionError(String.format(
                    "Expected no enqueued jobs of type '%s', but found %d", type, matches.size()));
        }
    }

    /** Assert that at least one job of the given type was performed. */
    public void assertPerformed(String type) {
        boolean found = performed.stream().anyMatch(j -> j.type().equals(type));
        if (!found) {
            throw new AssertionError(String.format(
                    "Expected at least one performed job of type '%s', found none", type));
        }
    }

    /** Assert that at least one job of the given type completed successfully. */
    public void assertCompleted(String type) {
        boolean found = performed.stream()
                .anyMatch(j -> j.type().equals(type) && "completed".equals(j.state()));
        if (!found) {
            throw new AssertionError(String.format(
                    "Expected a completed job of type '%s', found none", type));
        }
    }

    /** Assert that at least one job of the given type failed. */
    public void assertFailed(String type) {
        boolean found = performed.stream()
                .anyMatch(j -> j.type().equals(type) && "discarded".equals(j.state()));
        if (!found) {
            throw new AssertionError(String.format(
                    "Expected a failed job of type '%s', found none", type));
        }
    }

    // --- Utilities ---

    /** Return all enqueued jobs. */
    public List<FakeJob> allEnqueued() {
        return Collections.unmodifiableList(enqueued);
    }

    /** Return all enqueued jobs of the given type. */
    public List<FakeJob> allEnqueued(String type) {
        return enqueued.stream().filter(j -> j.type().equals(type)).toList();
    }

    /** Clear all enqueued and performed jobs. */
    public void clearAll() {
        enqueued.clear();
        performed.clear();
    }

    /** Process all available jobs using registered handlers. */
    public int drain() {
        int processed = 0;
        for (int i = 0; i < enqueued.size(); i++) {
            FakeJob job = enqueued.get(i);
            if (!"available".equals(job.state())) continue;

            FakeJob active = job.withState("active").withAttempt(job.attempt() + 1);
            Consumer<FakeJob> handler = handlers.get(job.type());

            FakeJob result;
            if (handler != null) {
                try {
                    handler.accept(active);
                    result = active.withState("completed");
                } catch (Exception e) {
                    result = active.withState("discarded");
                }
            } else {
                result = active.withState("completed");
            }

            enqueued.set(i, result);
            performed.add(result);
            processed++;
        }
        return processed;
    }

    // --- Internal ---

    private List<FakeJob> findMatching(List<FakeJob> jobs, String type, Map<String, Object> criteria) {
        return jobs.stream()
                .filter(j -> j.type().equals(type))
                .filter(j -> {
                    if (criteria == null) return true;
                    Object queue = criteria.get("queue");
                    if (queue != null && !queue.equals(j.queue())) return false;
                    return true;
                })
                .toList();
    }
}
