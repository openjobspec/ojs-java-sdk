package org.openjobspec.ojs.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openjobspec.ojs.OJSClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OJSTesting fake mode integration with OJSClient.
 */
class OJSTestingIntegrationTest {

    @AfterEach
    void tearDown() {
        var active = OJSTesting.getActive();
        if (active != null) {
            active.restore();
        }
    }

    @Test
    void fakeModeInterceptsEnqueue() {
        var testing = OJSTesting.fake();
        var client = testing.client();

        var job = client.enqueue("email.send", Map.of("to", "user@test.com"));

        assertNotNull(job);
        assertEquals("email.send", job.type());
        assertTrue(job.id().startsWith("fake-"));
        assertEquals("default", job.queue());
        assertEquals("available", job.state());

        testing.assertEnqueued("email.send");
        testing.assertEnqueuedCount("email.send", 1);
    }

    @Test
    void fakeModeInterceptsEnqueueWithOptions() {
        var testing = OJSTesting.fake();
        var client = testing.client();

        var job = client.enqueue("report.generate", (Object) Map.of("id", 42))
                .queue("reports")
                .send();

        assertNotNull(job);
        assertEquals("report.generate", job.type());
        assertEquals("reports", job.queue());

        testing.assertEnqueued("report.generate");
    }

    @Test
    void fakeModeTracksMultipleEnqueues() {
        var testing = OJSTesting.fake();
        var client = testing.client();

        client.enqueue("email.send", Map.of("to", "user1@test.com"));
        client.enqueue("email.send", Map.of("to", "user2@test.com"));
        client.enqueue("sms.send", Map.of("phone", "555-0100"));

        testing.assertEnqueuedCount("email.send", 2);
        testing.assertEnqueuedCount("sms.send", 1);
        testing.refuteEnqueued("push.send");
    }

    @Test
    void restoreDisablesFakeMode() {
        var testing = OJSTesting.fake();
        assertNotNull(OJSTesting.getActive());

        testing.restore();
        assertNull(OJSTesting.getActive());
    }

    @Test
    void drainProcessesFakeJobs() {
        var testing = OJSTesting.fake();
        var client = testing.client();

        client.enqueue("email.send", Map.of("to", "user@test.com"));

        int processed = testing.drain();
        assertEquals(1, processed);

        testing.assertCompleted("email.send");
    }

    @Test
    void transportCanBeUsedDirectly() {
        var testing = OJSTesting.fake();
        var client = OJSClient.withTransport(testing.transport());

        client.enqueue("email.send", Map.of("to", "user@test.com"));

        testing.assertEnqueued("email.send");
    }
}
