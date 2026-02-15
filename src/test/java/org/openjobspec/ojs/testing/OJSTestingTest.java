package org.openjobspec.ojs.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OJSTesting")
class OJSTestingTest {

    @AfterEach
    void tearDown() {
        var active = OJSTesting.getActive();
        if (active != null) {
            active.restore();
        }
    }

    @Nested
    @DisplayName("Fake mode lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("fake() activates testing mode")
        void fakeActivatesMode() {
            assertNull(OJSTesting.getActive());
            var testing = OJSTesting.fake();
            assertSame(testing, OJSTesting.getActive());
        }

        @Test
        @DisplayName("restore() deactivates testing mode")
        void restoreDeactivatesMode() {
            var testing = OJSTesting.fake();
            testing.restore();
            assertNull(OJSTesting.getActive());
        }

        @Test
        @DisplayName("restore() clears all state")
        void restoreClearsState() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("test.job", List.of(), "default", Map.of());
            testing.drain();

            testing.restore();

            var testing2 = OJSTesting.fake();
            assertEquals(0, testing2.allEnqueued().size());
        }
    }

    @Nested
    @DisplayName("assertEnqueued")
    class AssertEnqueuedTests {

        @Test
        @DisplayName("succeeds when job type was enqueued")
        void succeedsWhenEnqueued() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            assertDoesNotThrow(() -> testing.assertEnqueued("email.send"));
        }

        @Test
        @DisplayName("fails when no job of that type was enqueued")
        void failsWhenNotEnqueued() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("sms.send", List.of(), "default", Map.of());

            var error = assertThrows(AssertionError.class,
                    () -> testing.assertEnqueued("email.send"));
            assertTrue(error.getMessage().contains("email.send"));
            assertTrue(error.getMessage().contains("sms.send"));
        }

        @Test
        @DisplayName("fails when no jobs at all were enqueued")
        void failsWhenEmpty() {
            var testing = OJSTesting.fake();

            var error = assertThrows(AssertionError.class,
                    () -> testing.assertEnqueued("email.send"));
            assertTrue(error.getMessage().contains("email.send"));
        }

        @Test
        @DisplayName("succeeds with matching queue criteria")
        void succeedsWithQueueCriteria() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "email", Map.of());

            assertDoesNotThrow(() ->
                    testing.assertEnqueued("email.send", Map.of("queue", "email")));
        }

        @Test
        @DisplayName("fails with non-matching queue criteria")
        void failsWithNonMatchingQueue() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            assertThrows(AssertionError.class,
                    () -> testing.assertEnqueued("email.send", Map.of("queue", "email")));
        }
    }

    @Nested
    @DisplayName("assertEnqueuedCount")
    class AssertEnqueuedCountTests {

        @Test
        @DisplayName("succeeds with correct count")
        void succeedsWithCorrectCount() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            assertDoesNotThrow(() -> testing.assertEnqueuedCount("email.send", 2));
        }

        @Test
        @DisplayName("fails with incorrect count")
        void failsWithIncorrectCount() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var error = assertThrows(AssertionError.class,
                    () -> testing.assertEnqueuedCount("email.send", 3));
            assertTrue(error.getMessage().contains("3"));
            assertTrue(error.getMessage().contains("1"));
        }

        @Test
        @DisplayName("succeeds with zero count when type not enqueued")
        void succeedsWithZeroCount() {
            var testing = OJSTesting.fake();
            assertDoesNotThrow(() -> testing.assertEnqueuedCount("email.send", 0));
        }
    }

    @Nested
    @DisplayName("refuteEnqueued")
    class RefuteEnqueuedTests {

        @Test
        @DisplayName("succeeds when type not enqueued")
        void succeedsWhenNotEnqueued() {
            var testing = OJSTesting.fake();
            assertDoesNotThrow(() -> testing.refuteEnqueued("email.send"));
        }

        @Test
        @DisplayName("fails when type was enqueued")
        void failsWhenEnqueued() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var error = assertThrows(AssertionError.class,
                    () -> testing.refuteEnqueued("email.send"));
            assertTrue(error.getMessage().contains("email.send"));
            assertTrue(error.getMessage().contains("1"));
        }
    }

    @Nested
    @DisplayName("assertPerformed")
    class AssertPerformedTests {

        @Test
        @DisplayName("succeeds after drain processes a job")
        void succeedsAfterDrain() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.drain();

            assertDoesNotThrow(() -> testing.assertPerformed("email.send"));
        }

        @Test
        @DisplayName("fails when no job was performed")
        void failsWhenNotPerformed() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var error = assertThrows(AssertionError.class,
                    () -> testing.assertPerformed("email.send"));
            assertTrue(error.getMessage().contains("email.send"));
        }
    }

    @Nested
    @DisplayName("assertCompleted")
    class AssertCompletedTests {

        @Test
        @DisplayName("succeeds when job completed successfully")
        void succeedsWhenCompleted() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.drain();

            assertDoesNotThrow(() -> testing.assertCompleted("email.send"));
        }

        @Test
        @DisplayName("fails when job was not completed")
        void failsWhenNotCompleted() {
            var testing = OJSTesting.fake();

            var error = assertThrows(AssertionError.class,
                    () -> testing.assertCompleted("email.send"));
            assertTrue(error.getMessage().contains("email.send"));
        }

        @Test
        @DisplayName("fails when job failed instead of completing")
        void failsWhenJobFailed() {
            var testing = OJSTesting.fake();
            testing.registerHandler("email.send", job -> {
                throw new RuntimeException("handler error");
            });
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.drain();

            assertThrows(AssertionError.class,
                    () -> testing.assertCompleted("email.send"));
        }
    }

    @Nested
    @DisplayName("assertFailed")
    class AssertFailedTests {

        @Test
        @DisplayName("succeeds when job failed")
        void succeedsWhenFailed() {
            var testing = OJSTesting.fake();
            testing.registerHandler("email.send", job -> {
                throw new RuntimeException("handler error");
            });
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.drain();

            assertDoesNotThrow(() -> testing.assertFailed("email.send"));
        }

        @Test
        @DisplayName("fails when no job failed")
        void failsWhenNotFailed() {
            var testing = OJSTesting.fake();

            var error = assertThrows(AssertionError.class,
                    () -> testing.assertFailed("email.send"));
            assertTrue(error.getMessage().contains("email.send"));
        }

        @Test
        @DisplayName("fails when job completed instead of failing")
        void failsWhenJobCompleted() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.drain();

            assertThrows(AssertionError.class,
                    () -> testing.assertFailed("email.send"));
        }
    }

    @Nested
    @DisplayName("Utilities")
    class UtilityTests {

        @Test
        @DisplayName("allEnqueued() returns all enqueued jobs")
        void allEnqueuedReturnsAll() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.recordEnqueue("sms.send", List.of(), "default", Map.of());

            var all = testing.allEnqueued();
            assertEquals(2, all.size());
        }

        @Test
        @DisplayName("allEnqueued() returns unmodifiable list")
        void allEnqueuedIsUnmodifiable() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var all = testing.allEnqueued();
            assertThrows(UnsupportedOperationException.class, () -> all.clear());
        }

        @Test
        @DisplayName("allEnqueued(type) filters by type")
        void allEnqueuedByType() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.recordEnqueue("sms.send", List.of(), "default", Map.of());
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var emails = testing.allEnqueued("email.send");
            assertEquals(2, emails.size());
            assertTrue(emails.stream().allMatch(j -> "email.send".equals(j.type())));
        }

        @Test
        @DisplayName("clearAll() clears enqueued and performed")
        void clearAllClearsBoth() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.drain();

            testing.clearAll();

            assertEquals(0, testing.allEnqueued().size());
            assertThrows(AssertionError.class, () -> testing.assertPerformed("email.send"));
        }
    }

    @Nested
    @DisplayName("Drain")
    class DrainTests {

        @Test
        @DisplayName("drain with registered handler succeeds")
        void drainWithHandlerSucceeds() {
            var testing = OJSTesting.fake();
            testing.registerHandler("email.send", job -> {
                // Success handler
            });
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            int processed = testing.drain();
            assertEquals(1, processed);
            testing.assertCompleted("email.send");
        }

        @Test
        @DisplayName("drain with failing handler marks job as discarded")
        void drainWithFailingHandler() {
            var testing = OJSTesting.fake();
            testing.registerHandler("email.send", job -> {
                throw new RuntimeException("fail");
            });
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            int processed = testing.drain();
            assertEquals(1, processed);
            testing.assertFailed("email.send");
        }

        @Test
        @DisplayName("drain without handler auto-completes")
        void drainWithoutHandlerAutoCompletes() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            int processed = testing.drain();
            assertEquals(1, processed);
            testing.assertCompleted("email.send");
        }

        @Test
        @DisplayName("drain skips already-processed jobs")
        void drainSkipsProcessed() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            testing.drain();
            int secondDrain = testing.drain();
            assertEquals(0, secondDrain);
        }

        @Test
        @DisplayName("drain returns zero for empty queue")
        void drainEmptyReturnsZero() {
            var testing = OJSTesting.fake();
            assertEquals(0, testing.drain());
        }

        @Test
        @DisplayName("drain processes multiple jobs")
        void drainMultipleJobs() {
            var testing = OJSTesting.fake();
            testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            testing.recordEnqueue("sms.send", List.of(), "default", Map.of());
            testing.recordEnqueue("push.send", List.of(), "default", Map.of());

            int processed = testing.drain();
            assertEquals(3, processed);
            testing.assertCompleted("email.send");
            testing.assertCompleted("sms.send");
            testing.assertCompleted("push.send");
        }
    }

    @Nested
    @DisplayName("FakeJob")
    class FakeJobTests {

        @Test
        @DisplayName("recordEnqueue creates job with correct attributes")
        void recordEnqueueCreatesCorrectJob() {
            var testing = OJSTesting.fake();
            var job = testing.recordEnqueue("email.send",
                    List.of(Map.of("to", "user@test.com")),
                    "email",
                    Map.of("source", "test"));

            assertEquals("email.send", job.type());
            assertEquals("email", job.queue());
            assertEquals(List.of(Map.of("to", "user@test.com")), job.args());
            assertEquals(Map.of("source", "test"), job.meta());
            assertEquals("available", job.state());
            assertEquals(0, job.attempt());
            assertTrue(job.id().startsWith("fake-"));
            assertNotNull(job.createdAt());
        }

        @Test
        @DisplayName("recordEnqueue defaults null values")
        void recordEnqueueDefaultsNulls() {
            var testing = OJSTesting.fake();
            var job = testing.recordEnqueue("email.send", null, null, null);

            assertEquals("default", job.queue());
            assertEquals(List.of(), job.args());
            assertEquals(Map.of(), job.meta());
        }

        @Test
        @DisplayName("withState creates new FakeJob with updated state")
        void withStateCreatesNewJob() {
            var testing = OJSTesting.fake();
            var original = testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var active = original.withState("active");
            assertEquals("active", active.state());
            assertEquals("available", original.state());
            assertEquals(original.id(), active.id());
        }

        @Test
        @DisplayName("withAttempt creates new FakeJob with updated attempt")
        void withAttemptCreatesNewJob() {
            var testing = OJSTesting.fake();
            var original = testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            var retried = original.withAttempt(3);
            assertEquals(3, retried.attempt());
            assertEquals(0, original.attempt());
            assertEquals(original.id(), retried.id());
        }

        @Test
        @DisplayName("each recordEnqueue generates unique IDs")
        void uniqueIds() {
            var testing = OJSTesting.fake();
            var job1 = testing.recordEnqueue("email.send", List.of(), "default", Map.of());
            var job2 = testing.recordEnqueue("email.send", List.of(), "default", Map.of());

            assertNotEquals(job1.id(), job2.id());
        }
    }
}
