package org.openjobspec.ojs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DurableJobHandlerTest {

    static class TestHandler extends DurableJobHandler {
        @Override
        public Object handle(JobContext ctx) throws Exception { return null; }

        DurableJobHandler.DurableContext newContext(JobContext ctx) {
            return createDurableContext(ctx);
        }

        DurableJobHandler.DurableContext newContext(JobContext ctx, List<Map<String, Object>> log) {
            return createDurableContext(ctx, log);
        }
    }

    private final TestHandler handler = new TestHandler();

    private static JobContext ctx() {
        Job job = new Job(
                "1.0", "test-job-001", "test.type", "default",
                List.of(), Map.of(), 0, 30000,
                null, null, null, null, null,
                "active", 1,
                "2025-01-01T00:00:00Z", "2025-01-01T00:00:00Z",
                "2025-01-01T00:00:01Z", null, null, null, List.of());
        return new JobContext("http://localhost:8080", job, null, null, id -> {});
    }

    @Nested
    @DisplayName("DurableContext.now()")
    class NowTests {

        @Test
        @DisplayName("returns an Instant close to wall-clock time")
        void returnsInstant() {
            Instant before = Instant.now();
            Instant t = handler.newContext(ctx()).now();
            assertNotNull(t);
            assertFalse(t.isBefore(before));
        }

        @Test
        @DisplayName("records a 'now' entry in the replay log")
        void recordsEntry() {
            var dc = handler.newContext(ctx());
            dc.now();
            assertEquals(1, dc.getReplayLog().size());
            assertEquals("now", dc.getReplayLog().get(0).get("type"));
        }

        @Test
        @DisplayName("replays the same Instant from an existing log")
        void replays() {
            var dc1 = handler.newContext(ctx());
            Instant first = dc1.now();

            var dc2 = handler.newContext(ctx(), dc1.getReplayLog());
            assertEquals(first, dc2.now());
        }
    }

    @Nested
    @DisplayName("DurableContext.random()")
    class RandomTests {

        @Test
        @DisplayName("returns a lowercase hex string of 2*numBytes length")
        void returnsHex() {
            String hex = handler.newContext(ctx()).random(16);
            assertEquals(32, hex.length());
            assertTrue(hex.matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("records a 'random' entry in the replay log")
        void recordsEntry() {
            var dc = handler.newContext(ctx());
            dc.random(8);
            assertEquals(1, dc.getReplayLog().size());
            assertEquals("random", dc.getReplayLog().get(0).get("type"));
        }

        @Test
        @DisplayName("replays the same hex from an existing log")
        void replays() {
            var dc1 = handler.newContext(ctx());
            String first = dc1.random(16);

            var dc2 = handler.newContext(ctx(), dc1.getReplayLog());
            assertEquals(first, dc2.random(16));
        }
    }

    @Nested
    @DisplayName("DurableContext.sideEffect()")
    class SideEffectTests {

        @Test
        @DisplayName("executes the callable and returns its result")
        void executesCallable() throws Exception {
            String result = handler.newContext(ctx())
                    .sideEffect("op", () -> "hello", String.class);
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("records a 'side_effect' entry with the given key")
        void recordsEntry() throws Exception {
            var dc = handler.newContext(ctx());
            dc.sideEffect("api-call", () -> 42, Integer.class);
            assertEquals(1, dc.getReplayLog().size());
            assertEquals("side_effect", dc.getReplayLog().get(0).get("type"));
            assertEquals("api-call", dc.getReplayLog().get(0).get("key"));
        }

        @Test
        @DisplayName("replays without re-executing the callable")
        void replaysWithoutReExec() throws Exception {
            var dc1 = handler.newContext(ctx());
            dc1.sideEffect("op", () -> "original", String.class);

            var dc2 = handler.newContext(ctx(), dc1.getReplayLog());
            String replayed = dc2.sideEffect("op", () -> "different", String.class);
            assertEquals("original", replayed);
        }
    }

    @Nested
    @DisplayName("Multi-operation replay ordering")
    class ReplayOrderingTests {

        @Test
        @DisplayName("multiple operations replay in sequence order")
        void multipleOps() throws Exception {
            var dc1 = handler.newContext(ctx());
            Instant t = dc1.now();
            String r = dc1.random(8);
            String s = dc1.sideEffect("fetch", () -> "data", String.class);
            assertEquals(3, dc1.getReplayLog().size());

            var dc2 = handler.newContext(ctx(), dc1.getReplayLog());
            assertEquals(t, dc2.now());
            assertEquals(r, dc2.random(8));
            assertEquals("data", dc2.sideEffect("fetch", () -> "other", String.class));
        }

        @Test
        @DisplayName("new operations append after replayed entries")
        void appendAfterReplay() throws Exception {
            var dc1 = handler.newContext(ctx());
            dc1.now();

            var dc2 = handler.newContext(ctx(), dc1.getReplayLog());
            dc2.now();          // replayed
            dc2.random(4);      // new
            assertEquals(2, dc2.getReplayLog().size());
        }
    }

    @Nested
    @DisplayName("getReplayLog()")
    class ReplayLogTests {

        @Test
        @DisplayName("returns an empty list for a fresh context")
        void emptyByDefault() {
            assertTrue(handler.newContext(ctx()).getReplayLog().isEmpty());
        }

        @Test
        @DisplayName("returned list is unmodifiable")
        void unmodifiable() {
            var dc = handler.newContext(ctx());
            dc.now();
            assertThrows(UnsupportedOperationException.class,
                    () -> dc.getReplayLog().clear());
        }
    }
}
