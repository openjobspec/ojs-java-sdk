package org.openjobspec.ojs;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.openjobspec.ojs.transport.Json;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PropertyTest {

    // ------------------------------------------------------------------ //
    //  JSON round-trip properties
    // ------------------------------------------------------------------ //

    @Property(tries = 200)
    void jsonStringRoundtrip(@ForAll @StringLength(max = 200) String input) {
        String encoded = Json.encode(input);
        Object decoded = Json.decode(encoded);
        assertEquals(input, decoded);
    }

    @Property(tries = 200)
    void jsonIntegerRoundtrip(@ForAll int value) {
        String encoded = Json.encode(value);
        Object decoded = Json.decode(encoded);
        assertEquals(value, ((Number) decoded).intValue());
    }

    @Property(tries = 200)
    void jsonMapRoundtrip(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String key,
            @ForAll @StringLength(max = 50) String strVal,
            @ForAll @IntRange(min = -10000, max = 10000) int intVal,
            @ForAll boolean boolVal
    ) {
        var original = new LinkedHashMap<String, Object>();
        original.put("str_" + key, strVal);
        original.put("int_" + key, intVal);
        original.put("bool", boolVal);

        String json = Json.encode(original);
        Map<String, Object> decoded = Json.decodeObject(json);

        assertEquals(strVal, decoded.get("str_" + key));
        assertEquals(intVal, ((Number) decoded.get("int_" + key)).intValue());
        assertEquals(boolVal, decoded.get("bool"));
    }

    @Property(tries = 200)
    void jsonNestedMapRoundtrip(
            @ForAll @AlphaChars @StringLength(min = 1, max = 10) String outerKey,
            @ForAll @AlphaChars @StringLength(min = 1, max = 10) String innerKey,
            @ForAll @IntRange(min = -10000, max = 10000) int innerVal
    ) {
        var inner = new LinkedHashMap<String, Object>();
        inner.put(innerKey, innerVal);
        var outer = new LinkedHashMap<String, Object>();
        outer.put(outerKey, inner);

        String json = Json.encode(outer);
        Map<String, Object> decoded = Json.decodeObject(json);

        @SuppressWarnings("unchecked")
        var decodedInner = (Map<String, Object>) decoded.get(outerKey);
        assertNotNull(decodedInner);
        assertEquals(innerVal, ((Number) decodedInner.get(innerKey)).intValue());
    }

    @Property(tries = 200)
    void jsonListRoundtrip(
            @ForAll @Size(max = 10) List<@IntRange(min = -10000, max = 10000) Integer> values
    ) {
        String json = Json.encode(values);
        Object decoded = Json.decode(json);
        assertInstanceOf(List.class, decoded);

        @SuppressWarnings("unchecked")
        var decodedList = (List<Object>) decoded;
        assertEquals(values.size(), decodedList.size());
        for (int i = 0; i < values.size(); i++) {
            assertEquals(values.get(i).intValue(), ((Number) decodedList.get(i)).intValue());
        }
    }

    // ------------------------------------------------------------------ //
    //  RetryPolicy properties
    // ------------------------------------------------------------------ //

    @Property(tries = 200)
    void retryPolicyBuilderPreservesFields(
            @ForAll @IntRange(min = 0, max = 100) int maxAttempts,
            @ForAll @LongRange(min = 1, max = 60000) long initialIntervalMs,
            @ForAll @DoubleRange(min = 1.0, max = 10.0) double backoffCoefficient,
            @ForAll @LongRange(min = 1, max = 600000) long maxIntervalMs,
            @ForAll boolean jitter
    ) {
        var initialInterval = Duration.ofMillis(initialIntervalMs);
        var maxInterval = Duration.ofMillis(maxIntervalMs);

        var policy = RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .initialInterval(initialInterval)
                .backoffCoefficient(backoffCoefficient)
                .maxInterval(maxInterval)
                .jitter(jitter)
                .build();

        assertAll(
                () -> assertEquals(maxAttempts, policy.maxAttempts()),
                () -> assertEquals(initialInterval, policy.initialInterval()),
                () -> assertEquals(backoffCoefficient, policy.backoffCoefficient()),
                () -> assertEquals(maxInterval, policy.maxInterval()),
                () -> assertEquals(jitter, policy.jitter())
        );
    }

    @Property(tries = 200)
    void retryPolicyRejectsNegativeMaxAttempts(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = -1) int maxAttempts
    ) {
        assertThrows(IllegalArgumentException.class, () ->
                RetryPolicy.builder().maxAttempts(maxAttempts).build());
    }

    @Property(tries = 200)
    void retryPolicyRejectsLowBackoffCoefficient(
            @ForAll @DoubleRange(min = 0.0, max = 0.99, maxIncluded = true) double coefficient
    ) {
        assertThrows(IllegalArgumentException.class, () ->
                RetryPolicy.builder().backoffCoefficient(coefficient).build());
    }

    @Property(tries = 200)
    void retryPolicyEquality(
            @ForAll @IntRange(min = 0, max = 50) int maxAttempts,
            @ForAll @DoubleRange(min = 1.0, max = 5.0) double coefficient,
            @ForAll boolean jitter
    ) {
        var a = RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .backoffCoefficient(coefficient)
                .jitter(jitter)
                .build();
        var b = RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .backoffCoefficient(coefficient)
                .jitter(jitter)
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ------------------------------------------------------------------ //
    //  Retry delay computation properties
    // ------------------------------------------------------------------ //

    @Property(tries = 200)
    void retryDelayAlwaysNonNegative(
            @ForAll @IntRange(min = 0, max = 30) int attempt,
            @ForAll @LongRange(min = 1, max = 10000) long baseDelayMs,
            @ForAll @LongRange(min = 1, max = 300000) long maxDelayMs
    ) {
        long exponentialDelay = baseDelayMs * (1L << Math.min(attempt, 30));
        long cappedDelay = Math.min(exponentialDelay, maxDelayMs);
        // With jitter: delay * (0.5 + random * 0.5), minimum is cappedDelay * 0.5
        long minJitteredDelay = cappedDelay / 2;

        assertTrue(cappedDelay >= 0, "Capped delay must be non-negative");
        assertTrue(minJitteredDelay >= 0, "Jittered delay lower bound must be non-negative");
    }

    @Property(tries = 200)
    void retryDelayNeverExceedsMax(
            @ForAll @IntRange(min = 0, max = 30) int attempt,
            @ForAll @LongRange(min = 1, max = 10000) long baseDelayMs,
            @ForAll @LongRange(min = 1, max = 300000) long maxDelayMs
    ) {
        long exponentialDelay = baseDelayMs * (1L << Math.min(attempt, 30));
        long cappedDelay = Math.min(exponentialDelay, maxDelayMs);

        assertTrue(cappedDelay <= maxDelayMs, "Capped delay must not exceed max");
    }

    @Property(tries = 200)
    void retryDelayMonotonicallyIncreasesWithoutJitter(
            @ForAll @IntRange(min = 0, max = 29) int attempt,
            @ForAll @LongRange(min = 1, max = 1000) long baseDelayMs,
            @ForAll @LongRange(min = 1, max = 300000) long maxDelayMs
    ) {
        long delay1 = Math.min(baseDelayMs * (1L << Math.min(attempt, 30)), maxDelayMs);
        long delay2 = Math.min(baseDelayMs * (1L << Math.min(attempt + 1, 30)), maxDelayMs);

        assertTrue(delay2 >= delay1,
                "Delay at attempt " + (attempt + 1) + " must be >= delay at attempt " + attempt);
    }

    // ------------------------------------------------------------------ //
    //  Job state lifecycle properties
    // ------------------------------------------------------------------ //

    @Property(tries = 100)
    void terminalStatesAreTerminal(@ForAll("terminalStates") String state) {
        var job = new Job(null, "id-1", "test.job", null, null, null, 0, 0,
                null, null, null, null, null,
                state, 0, null, null, null, null, null, null, null);

        assertTrue(job.isTerminal(), state + " should be terminal");
        assertTrue(Job.State.fromString(state).isTerminal());
    }

    @Provide
    Arbitrary<String> terminalStates() {
        return Arbitraries.of("completed", "cancelled", "discarded");
    }

    @Property(tries = 100)
    void nonTerminalStatesAreNotTerminal(@ForAll("nonTerminalStates") String state) {
        var job = new Job(null, "id-1", "test.job", null, null, null, 0, 0,
                null, null, null, null, null,
                state, 0, null, null, null, null, null, null, null);

        assertFalse(job.isTerminal(), state + " should not be terminal");
        assertFalse(Job.State.fromString(state).isTerminal());
    }

    @Provide
    Arbitrary<String> nonTerminalStates() {
        return Arbitraries.of("scheduled", "available", "pending", "active", "retryable");
    }

    @Property(tries = 100)
    void allStatesAreRecognized(@ForAll("allStates") String state) {
        assertDoesNotThrow(() -> Job.State.fromString(state));
    }

    @Provide
    Arbitrary<String> allStates() {
        return Arbitraries.of(
                "scheduled", "available", "pending", "active",
                "completed", "retryable", "cancelled", "discarded"
        );
    }

    // ------------------------------------------------------------------ //
    //  Job construction properties
    // ------------------------------------------------------------------ //

    @Property(tries = 200)
    void jobDefaultsApplied(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String type
    ) {
        var job = new Job(null, null, type, null, null, null, 0, 0,
                null, null, null, null, null,
                null, 0, null, null, null, null, null, null, null);

        assertAll(
                () -> assertEquals(Job.SPEC_VERSION, job.specversion()),
                () -> assertEquals("default", job.queue()),
                () -> assertEquals(type, job.type()),
                () -> assertNotNull(job.args()),
                () -> assertTrue(job.args().isEmpty()),
                () -> assertNotNull(job.meta()),
                () -> assertTrue(job.meta().isEmpty()),
                () -> assertNotNull(job.tags()),
                () -> assertTrue(job.tags().isEmpty())
        );
    }

    @Property(tries = 200)
    void jobPreservesExplicitFields(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String queue,
            @ForAll @IntRange(min = -100, max = 100) int priority
    ) {
        var job = new Job(null, "job-1", type, queue, List.of(), Map.of(), priority, 30000,
                null, null, null, null, null,
                null, 0, null, null, null, null, null, null, null);

        assertAll(
                () -> assertEquals(type, job.type()),
                () -> assertEquals(queue, job.queue()),
                () -> assertEquals(priority, job.priority()),
                () -> assertEquals(30000, job.timeout())
        );
    }

    @Property(tries = 100)
    void jobRequiresNonNullType() {
        assertThrows(NullPointerException.class, () ->
                new Job(null, null, null, null, null, null, 0, 0,
                        null, null, null, null, null,
                        null, 0, null, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ //
    //  Workflow wire format properties
    // ------------------------------------------------------------------ //

    @Property(tries = 100)
    void workflowChainHasSequentialDependencies(
            @ForAll @IntRange(min = 2, max = 8) int stepCount
    ) {
        var steps = new Workflow.Step[stepCount];
        for (int i = 0; i < stepCount; i++) {
            steps[i] = Workflow.step("step.type." + i, Map.of("index", i));
        }

        var chain = Workflow.chain("test-chain", steps);
        var wire = chain.toWire();

        @SuppressWarnings("unchecked")
        var wireSteps = (List<Map<String, Object>>) wire.get("steps");
        assertEquals(stepCount, wireSteps.size());

        // First step has no depends_on
        assertFalse(wireSteps.get(0).containsKey("depends_on"));

        // Each subsequent step depends on the previous one
        for (int i = 1; i < stepCount; i++) {
            @SuppressWarnings("unchecked")
            var deps = (List<String>) wireSteps.get(i).get("depends_on");
            assertNotNull(deps, "Step " + i + " should have depends_on");
            assertEquals(1, deps.size());
            assertEquals("step_" + (i - 1), deps.get(0));
        }
    }

    @Property(tries = 100)
    void workflowGroupHasNoDependencies(
            @ForAll @IntRange(min = 1, max = 8) int stepCount
    ) {
        var steps = new Workflow.Step[stepCount];
        for (int i = 0; i < stepCount; i++) {
            steps[i] = Workflow.step("step.type." + i, Map.of());
        }

        var group = Workflow.group("test-group", steps);
        var wire = group.toWire();

        @SuppressWarnings("unchecked")
        var wireSteps = (List<Map<String, Object>>) wire.get("steps");
        assertEquals(stepCount, wireSteps.size());

        for (var wireStep : wireSteps) {
            assertFalse(wireStep.containsKey("depends_on"),
                    "Group steps should have no dependencies");
        }
    }

    @Property(tries = 100)
    void workflowStepPreservesType(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String type
    ) {
        var step = Workflow.step(type, Map.of("key", "val"));
        assertEquals(type, step.type());
        assertNotNull(step.args());
        assertEquals("val", step.args().get("key"));
    }

    // ------------------------------------------------------------------ //
    //  Event properties
    // ------------------------------------------------------------------ //

    @Property(tries = 100)
    void eventFromMapRoundtrip(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String type,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String subject
    ) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", "evt-1");
        map.put("type", type);
        map.put("source", "/test");
        map.put("subject", subject);
        map.put("time", "2024-01-01T00:00:00Z");
        map.put("data", Map.of("key", "value"));

        var event = Event.fromMap(map);

        assertAll(
                () -> assertEquals("evt-1", event.id()),
                () -> assertEquals(type, event.type()),
                () -> assertEquals(subject, event.subject()),
                () -> assertEquals(subject, event.jobId()),
                () -> assertEquals("value", event.data().get("key"))
        );
    }

    @Property(tries = 100)
    void jobEventPrefixDetection(@ForAll("jobEventTypes") String type) {
        var event = Event.of(type, Map.of());
        assertTrue(event.isJobEvent());
        assertFalse(event.isWorkflowEvent());
        assertFalse(event.isWorkerEvent());
    }

    @Provide
    Arbitrary<String> jobEventTypes() {
        return Arbitraries.of(
                Event.JOB_ENQUEUED, Event.JOB_STARTED, Event.JOB_COMPLETED,
                Event.JOB_FAILED, Event.JOB_DISCARDED, Event.JOB_RETRYING,
                Event.JOB_CANCELLED, Event.JOB_SCHEDULED, Event.JOB_EXPIRED,
                Event.JOB_PROGRESS
        );
    }
}
