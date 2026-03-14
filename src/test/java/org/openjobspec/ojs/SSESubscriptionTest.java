package org.openjobspec.ojs;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SSESubscriptionTest {

    private HttpServer startSSEServer(String ssePayload) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ojs/v1/events/stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ssePayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        });
        server.start();
        return server;
    }

    @Test
    void parsesSingleEvent() throws Exception {
        String payload = "event: job.completed\nid: evt-1\ndata: {\"ok\":true}\n\n";
        var server = startSSEServer(payload);
        int port = server.getAddress().getPort();

        var events = new ArrayList<SSESubscription.SSEEvent>();
        var latch = new CountDownLatch(1);

        var sub = SSESubscription.subscribe(
            "http://localhost:" + port,
            "queue:default",
            event -> {
                events.add(event);
                latch.countDown();
            }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS), "should receive event within 5s");
        sub.cancel();
        server.stop(0);

        assertEquals(1, events.size());
        assertEquals("job.completed", events.get(0).type());
        assertEquals("evt-1", events.get(0).id());
        assertEquals("{\"ok\":true}", events.get(0).data());
    }

    @Test
    void parsesMultilineData() throws Exception {
        String payload = "event: job.completed\ndata: {\"part1\":\ndata: \"value\"}\n\n";
        var server = startSSEServer(payload);
        int port = server.getAddress().getPort();

        var events = new ArrayList<SSESubscription.SSEEvent>();
        var latch = new CountDownLatch(1);

        var sub = SSESubscription.subscribe(
            "http://localhost:" + port,
            "all",
            event -> {
                events.add(event);
                latch.countDown();
            }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        sub.cancel();
        server.stop(0);

        assertEquals(1, events.size());
        assertEquals("{\"part1\":\n\"value\"}", events.get(0).data());
    }

    @Test
    void handlesFieldsWithoutSpaceAfterColon() throws Exception {
        String payload = "event:job.failed\nid:evt-2\ndata:{\"state\":\"failed\"}\n\n";
        var server = startSSEServer(payload);
        int port = server.getAddress().getPort();

        var events = new ArrayList<SSESubscription.SSEEvent>();
        var latch = new CountDownLatch(1);

        var sub = SSESubscription.subscribe(
            "http://localhost:" + port,
            "all",
            event -> {
                events.add(event);
                latch.countDown();
            }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        sub.cancel();
        server.stop(0);

        assertEquals(1, events.size());
        assertEquals("job.failed", events.get(0).type());
        assertEquals("evt-2", events.get(0).id());
    }

    @Test
    void parsesMultipleEvents() throws Exception {
        String payload = "event: job.active\ndata: {\"n\":1}\n\nevent: job.completed\ndata: {\"n\":2}\n\n";
        var server = startSSEServer(payload);
        int port = server.getAddress().getPort();

        var events = new ArrayList<SSESubscription.SSEEvent>();
        var latch = new CountDownLatch(2);

        var sub = SSESubscription.subscribe(
            "http://localhost:" + port,
            "all",
            event -> {
                events.add(event);
                latch.countDown();
            }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        sub.cancel();
        server.stop(0);

        assertEquals(2, events.size());
        assertEquals("job.active", events.get(0).type());
        assertEquals("job.completed", events.get(1).type());
    }

    @Test
    void usesDefaultMessageTypeWhenNoEventField() throws Exception {
        String payload = "data: {\"ping\":true}\n\n";
        var server = startSSEServer(payload);
        int port = server.getAddress().getPort();

        var events = new ArrayList<SSESubscription.SSEEvent>();
        var latch = new CountDownLatch(1);

        var sub = SSESubscription.subscribe(
            "http://localhost:" + port,
            "all",
            event -> {
                events.add(event);
                latch.countDown();
            }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        sub.cancel();
        server.stop(0);

        assertEquals("message", events.get(0).type());
    }

    @Test
    void cancelStopsReceiving() throws Exception {
        var sub = SSESubscription.subscribe(
            "http://localhost:1", // Invalid port — will fail to connect
            "all",
            event -> {}
        );
        // Should not throw
        sub.cancel();
        sub.cancel(); // Idempotent
    }
}
