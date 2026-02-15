package org.openjobspec.ojs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openjobspec.ojs.OJSError.OJSException;
import org.openjobspec.ojs.transport.Transport;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link OJSWorker} operations against a mocked {@link Transport}.
 * Verifies fetch/ack/nack contract, handler invocation, and middleware execution.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OJSWorkerTransportTest {

    @Mock
    Transport transport;

    // -----------------------------------------------------------------------
    // Builder with Transport
    // -----------------------------------------------------------------------

    @Nested
    class BuilderTests {

        @Test
        void builderWithTransportDoesNotRequireUrl() {
            var worker = OJSWorker.builder()
                    .transport(transport)
                    .build();
            assertNotNull(worker);
        }

        @Test
        void builderWithoutUrlOrTransportThrows() {
            assertThrows(NullPointerException.class,
                    () -> OJSWorker.builder().build());
        }
    }

    // -----------------------------------------------------------------------
    // Job Processing (fetch → handle → ack)
    // -----------------------------------------------------------------------

    @Nested
    class JobProcessingTests {

        @Test
        void workerFetchesAndAcksJob() throws Exception {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "job_1");
            jobMap.put("type", "email.send");
            jobMap.put("state", "active");
            jobMap.put("args", List.of(Map.of("to", "user@test.com")));

            var fetchResponse = Map.<String, Object>of("jobs", List.of(jobMap));
            var emptyFetch = Map.<String, Object>of("jobs", List.of());

            // First fetch returns a job, subsequent fetches return empty
            when(transport.post(eq("/workers/fetch"), any()))
                    .thenReturn(fetchResponse)
                    .thenReturn(emptyFetch);

            // ACK should succeed
            when(transport.post(eq("/workers/ack"), any()))
                    .thenReturn(Map.of());

            // Heartbeat should succeed
            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of());

            var handlerCalled = new CountDownLatch(1);
            var receivedType = new AtomicReference<String>();

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(500))
                    .build();

            worker.register("email.send", ctx -> {
                receivedType.set(ctx.job().type());
                handlerCalled.countDown();
                return Map.of("sent", true);
            });

            // Start in background thread
            var workerThread = Thread.startVirtualThread(worker::start);

            // Wait for handler to be called
            assertTrue(handlerCalled.await(5, TimeUnit.SECONDS),
                    "Handler should be called within 5 seconds");

            worker.stop();
            workerThread.join(5000);

            assertEquals("email.send", receivedType.get());

            // Verify fetch was called with correct body
            verify(transport, atLeastOnce()).post(eq("/workers/fetch"), argThat(body -> {
                assertNotNull(body.get("worker_id"));
                assertNotNull(body.get("queues"));
                assertNotNull(body.get("count"));
                return true;
            }));

            // Verify ACK was called with job_id and result
            verify(transport).post(eq("/workers/ack"), argThat(body -> {
                assertEquals("job_1", body.get("job_id"));
                assertNotNull(body.get("result"));
                return true;
            }));
        }

        @Test
        void workerNacksJobWhenHandlerThrows() throws Exception {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "job_2");
            jobMap.put("type", "failing.task");
            jobMap.put("state", "active");

            when(transport.post(eq("/workers/fetch"), any()))
                    .thenReturn(Map.<String, Object>of("jobs", List.of(jobMap)))
                    .thenReturn(Map.<String, Object>of("jobs", List.of()));

            when(transport.post(eq("/workers/nack"), any()))
                    .thenReturn(Map.of());

            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of());

            var handlerCalled = new CountDownLatch(1);

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(500))
                    .build();

            worker.register("failing.task", ctx -> {
                handlerCalled.countDown();
                throw new RuntimeException("Something went wrong");
            });

            var workerThread = Thread.startVirtualThread(worker::start);
            assertTrue(handlerCalled.await(5, TimeUnit.SECONDS));

            // Give worker time to nack
            Thread.sleep(200);

            worker.stop();
            workerThread.join(5000);

            // Verify NACK was called with error info
            verify(transport).post(eq("/workers/nack"), argThat(body -> {
                assertEquals("job_2", body.get("job_id"));
                @SuppressWarnings("unchecked")
                var error = (Map<String, Object>) body.get("error");
                assertNotNull(error);
                assertEquals("RuntimeException", ((Map<String, Object>) error.get("details")).get("error_class"));
                assertEquals("handler_error", error.get("code"));
                assertEquals("Something went wrong", error.get("message"));
                return true;
            }));
        }

        @Test
        void workerNacksJobWhenNoHandlerRegistered() throws Exception {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "job_3");
            jobMap.put("type", "unknown.type");
            jobMap.put("state", "active");

            when(transport.post(eq("/workers/fetch"), any()))
                    .thenReturn(Map.<String, Object>of("jobs", List.of(jobMap)))
                    .thenReturn(Map.<String, Object>of("jobs", List.of()));

            when(transport.post(eq("/workers/nack"), any()))
                    .thenReturn(Map.of());

            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of());

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(500))
                    .build();

            // Register a different handler
            worker.register("other.type", ctx -> null);

            var workerThread = Thread.startVirtualThread(worker::start);
            Thread.sleep(500);

            worker.stop();
            workerThread.join(5000);

            // Verify NACK was called with handler_not_found
            verify(transport).post(eq("/workers/nack"), argThat(body -> {
                assertEquals("job_3", body.get("job_id"));
                @SuppressWarnings("unchecked")
                var error = (Map<String, Object>) body.get("error");
                assertEquals("handler_not_found", error.get("code"));
                return true;
            }));
        }
    }

    // -----------------------------------------------------------------------
    // Middleware Integration
    // -----------------------------------------------------------------------

    @Nested
    class MiddlewareTests {

        @Test
        void middlewareExecutesDuringJobProcessing() throws Exception {
            var jobMap = new HashMap<String, Object>();
            jobMap.put("id", "job_mw");
            jobMap.put("type", "task.run");
            jobMap.put("state", "active");

            when(transport.post(eq("/workers/fetch"), any()))
                    .thenReturn(Map.<String, Object>of("jobs", List.of(jobMap)))
                    .thenReturn(Map.<String, Object>of("jobs", List.of()));

            when(transport.post(eq("/workers/ack"), any()))
                    .thenReturn(Map.of());

            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of());

            var executionOrder = Collections.synchronizedList(new ArrayList<String>());
            var done = new CountDownLatch(1);

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(500))
                    .build();

            worker.use((ctx, next) -> {
                executionOrder.add("before");
                next.handle(ctx);
                executionOrder.add("after");
            });

            worker.register("task.run", ctx -> {
                executionOrder.add("handler");
                done.countDown();
                return "ok";
            });

            var workerThread = Thread.startVirtualThread(worker::start);
            assertTrue(done.await(5, TimeUnit.SECONDS));

            // Give time for middleware after-processing and ack
            Thread.sleep(200);

            worker.stop();
            workerThread.join(5000);

            assertEquals(List.of("before", "handler", "after"), executionOrder);
        }
    }

    // -----------------------------------------------------------------------
    // Heartbeat
    // -----------------------------------------------------------------------

    @Nested
    class HeartbeatTests {

        @Test
        void workerSendsPeriodicHeartbeats() throws Exception {
            when(transport.post(eq("/workers/fetch"), any()))
                    .thenReturn(Map.<String, Object>of("jobs", List.of()));

            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of());

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(100))
                    .build();

            worker.register("any.type", ctx -> null);

            var workerThread = Thread.startVirtualThread(worker::start);
            Thread.sleep(500);
            worker.stop();
            workerThread.join(5000);

            // Should have sent multiple heartbeats
            verify(transport, atLeast(2)).post(eq("/workers/heartbeat"), argThat(body -> {
                assertNotNull(body.get("worker_id"));
                assertNotNull(body.get("active_jobs"));
                return true;
            }));
        }

        @Test
        void workerRespectsServerQuietDirective() throws Exception {
            when(transport.post(eq("/workers/fetch"), any()))
                    .thenReturn(Map.<String, Object>of("jobs", List.of()));

            // Server tells worker to go quiet
            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of("state", "quiet"));

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(100))
                    .build();

            worker.register("any.type", ctx -> null);

            var workerThread = Thread.startVirtualThread(worker::start);
            Thread.sleep(500);

            assertEquals(OJSWorker.State.QUIET, worker.getState());

            worker.stop();
            workerThread.join(5000);
        }
    }

    // -----------------------------------------------------------------------
    // Transport error resilience
    // -----------------------------------------------------------------------

    @Nested
    class ErrorResilienceTests {

        @Test
        void workerContinuesAfterFetchError() throws Exception {
            // First fetch throws, subsequent calls succeed with empty result
            when(transport.post(eq("/workers/fetch"), any()))
                    .thenThrow(new OJSException(new OJSError.TransportError(
                            "transport_error", "Connection reset", null)))
                    .thenReturn(Map.<String, Object>of("jobs", List.of()));

            when(transport.post(eq("/workers/heartbeat"), any()))
                    .thenReturn(Map.of());

            var worker = OJSWorker.builder()
                    .transport(transport)
                    .concurrency(1)
                    .pollInterval(Duration.ofMillis(50))
                    .heartbeatInterval(Duration.ofMillis(500))
                    .build();

            worker.register("any.type", ctx -> null);

            var workerThread = Thread.startVirtualThread(worker::start);
            Thread.sleep(1000);
            worker.stop();
            workerThread.join(5000);

            // Verify it retried after the error (at least the error + one successful call)
            verify(transport, atLeast(2)).post(eq("/workers/fetch"), any());
        }
    }
}
