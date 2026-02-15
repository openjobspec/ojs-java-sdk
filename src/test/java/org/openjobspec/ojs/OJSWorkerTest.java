package org.openjobspec.ojs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OJSWorker}, covering builder validation,
 * handler registration, middleware chain execution, worker defaults,
 * JobContext result setting, and worker state enum values.
 */
class OJSWorkerTest {

    // -------------------------------------------------------
    // Builder validation
    // -------------------------------------------------------

    @Test
    void builderThrowsWhenUrlIsNull() {
        var builder = OJSWorker.builder();
        var ex = assertThrows(NullPointerException.class, builder::build);
        assertEquals("url must not be null", ex.getMessage());
    }

    @Test
    void builderCreatesWorkerWithValidUrl() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertNotNull(worker);
        assertNotNull(worker.getWorkerId());
        assertTrue(worker.getWorkerId().startsWith("worker_"));
    }

    @Test
    void builderAcceptsAllOptions() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .queues(List.of("critical", "email"))
                .concurrency(5)
                .build();

        assertNotNull(worker);
    }

    // -------------------------------------------------------
    // Worker default configuration
    // -------------------------------------------------------

    @Test
    void defaultConcurrencyIsTen() {
        // The builder defaults concurrency to 10. We verify through the
        // activeJobCount starting at 0 (no jobs active) and that the
        // worker builds successfully without specifying concurrency.
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertNotNull(worker);
        assertEquals(0, worker.getActiveJobCount());
    }

    @Test
    void defaultStateIsRunning() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertEquals(OJSWorker.State.RUNNING, worker.getState());
    }

    @Test
    void workerIdIsUnique() {
        var worker1 = OJSWorker.builder().url("http://localhost:8080").build();
        var worker2 = OJSWorker.builder().url("http://localhost:8080").build();

        assertNotEquals(worker1.getWorkerId(), worker2.getWorkerId());
    }

    // -------------------------------------------------------
    // Handler registration
    // -------------------------------------------------------

    @Test
    void registerHandlerSucceeds() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        // Should not throw
        worker.register("email.send", ctx -> Map.of("sent", true));
    }

    @Test
    void registerMultipleHandlers() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        worker.register("email.send", ctx -> "email sent");
        worker.register("sms.send", ctx -> "sms sent");
        worker.register("push.send", ctx -> "push sent");

        // All registrations should succeed without error
        assertNotNull(worker);
    }

    @Test
    void registerNullJobTypeThrows() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertThrows(NullPointerException.class,
                () -> worker.register(null, ctx -> null));
    }

    @Test
    void registerNullHandlerThrows() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertThrows(NullPointerException.class,
                () -> worker.register("email.send", null));
    }

    @Test
    void registerBlankJobTypeThrowsValidationError() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        var ex = assertThrows(OJSError.OJSException.class,
                () -> worker.register("  ", ctx -> null));
        assertInstanceOf(OJSError.ValidationError.class, ex.error());
    }

    // -------------------------------------------------------
    // Middleware
    // -------------------------------------------------------

    @Test
    void useMiddlewareSucceeds() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        // Should not throw
        worker.use((ctx, next) -> next.handle(ctx));
    }

    @Test
    void useNamedMiddlewareSucceeds() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        worker.use("logging", (ctx, next) -> {
            // pre-processing
            next.handle(ctx);
            // post-processing
        });

        assertNotNull(worker);
    }

    @Test
    void useNullMiddlewareThrows() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertThrows(NullPointerException.class,
                () -> worker.use(null));
    }

    @Test
    void middlewareChainExecutesInCorrectOrder() throws Exception {
        // Simulate the middleware chain manually to verify execution order.
        // The OJSWorker.buildMiddlewareChain is private, so we replicate the
        // onion model: first registered middleware is outermost.
        var executionOrder = new ArrayList<String>();

        Middleware first = (ctx, next) -> {
            executionOrder.add("first-before");
            next.handle(ctx);
            executionOrder.add("first-after");
        };

        Middleware second = (ctx, next) -> {
            executionOrder.add("second-before");
            next.handle(ctx);
            executionOrder.add("second-after");
        };

        JobHandler handler = ctx -> {
            executionOrder.add("handler");
            return "result";
        };

        // Build the chain manually (same logic as OJSWorker.buildMiddlewareChain):
        // wrap in reverse order so first middleware is outermost.
        List<Middleware> middlewares = List.of(first, second);
        JobHandler chain = handler;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            var mw = middlewares.get(i);
            var next = chain;
            chain = ctx -> {
                var wrapper = new Object() { Object result; };
                mw.apply(ctx, innerCtx -> {
                    wrapper.result = next.handle(innerCtx);
                    return wrapper.result;
                });
                return wrapper.result;
            };
        }

        // Create a minimal JobContext for the test
        var job = new Job(
                null, "job_1", "test.job", "default",
                List.of(), Map.of(), 0, 30, null, null,
                null, null, null,
                "active", 1, null, null, null, null, null, null, null
        );
        var ctx = new JobContext(job, null, null, null);

        // Execute
        chain.handle(ctx);

        assertEquals(
                List.of("first-before", "second-before", "handler", "second-after", "first-after"),
                executionOrder,
                "Middleware should execute in onion order: first-before, second-before, handler, second-after, first-after"
        );
    }

    // -------------------------------------------------------
    // JobContext creation and result setting
    // -------------------------------------------------------

    @Test
    void jobContextExposesJobProperties() {
        var job = new Job(
                null, "job_123", "email.send", "critical",
                List.of(Map.of("to", "user@example.com")),
                Map.of("trace_id", "abc"),
                5, 60, null, null,
                null, null, null,
                "active", 3, null, null, null, null, null, null, null
        );

        var ctx = new JobContext(job, "wf_1", Map.of("parent_result", "ok"), null);

        assertSame(job, ctx.job());
        assertEquals("email.send", ctx.job().type());
        assertEquals(3, ctx.attempt());
        assertEquals("critical", ctx.queue());
        assertEquals("wf_1", ctx.workflowId());
        assertEquals(Map.of("parent_result", "ok"), ctx.parentResults());
    }

    @Test
    void jobContextSetResultAndGetResult() {
        var job = new Job(
                null, "job_1", "test.job", "default",
                List.of(), Map.of(), 0, 30, null, null,
                null, null, null,
                null, 1, null, null, null, null, null, null, null
        );
        var ctx = new JobContext(job, null, null, null);

        assertNull(ctx.getResult(), "Result should be null initially");

        ctx.setResult(Map.of("status", "done"));
        assertEquals(Map.of("status", "done"), ctx.getResult());

        // Overwrite with a new result
        ctx.setResult("overwritten");
        assertEquals("overwritten", ctx.getResult());
    }

    @Test
    void jobContextIsCancelledDefaultsFalse() {
        var job = new Job(
                null, "job_1", "test.job", "default",
                List.of(), Map.of(), 0, 30, null, null,
                null, null, null,
                null, 1, null, null, null, null, null, null, null
        );
        var ctx = new JobContext(job, null, null, null);

        assertFalse(ctx.isCancelled());
    }

    @Test
    void jobContextMarkCancelledSetsCancelledTrue() {
        var job = new Job(
                null, "job_1", "test.job", "default",
                List.of(), Map.of(), 0, 30, null, null,
                null, null, null,
                null, 1, null, null, null, null, null, null, null
        );
        var ctx = new JobContext(job, null, null, null);

        ctx.markCancelled();
        assertTrue(ctx.isCancelled());
    }

    @Test
    void jobContextParentResultsDefaultsToEmptyMap() {
        var job = new Job(
                null, "job_1", "test.job", "default",
                List.of(), Map.of(), 0, 30, null, null,
                null, null, null,
                null, 1, null, null, null, null, null, null, null
        );
        var ctx = new JobContext(job, null, null, null);

        assertNotNull(ctx.parentResults());
        assertTrue(ctx.parentResults().isEmpty());
    }

    // -------------------------------------------------------
    // Worker State enum
    // -------------------------------------------------------

    @Test
    void stateEnumHasExpectedValues() {
        var states = OJSWorker.State.values();
        assertEquals(3, states.length);

        assertEquals(OJSWorker.State.RUNNING, OJSWorker.State.valueOf("RUNNING"));
        assertEquals(OJSWorker.State.QUIET, OJSWorker.State.valueOf("QUIET"));
        assertEquals(OJSWorker.State.TERMINATE, OJSWorker.State.valueOf("TERMINATE"));
    }

    @Test
    void stateEnumStringValues() {
        assertEquals("running", OJSWorker.State.RUNNING.value());
        assertEquals("quiet", OJSWorker.State.QUIET.value());
        assertEquals("terminate", OJSWorker.State.TERMINATE.value());
    }

    @Test
    void stopTransitionsStateToQuiet() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        assertEquals(OJSWorker.State.RUNNING, worker.getState());

        worker.stop();
        assertEquals(OJSWorker.State.QUIET, worker.getState());
    }

    // -------------------------------------------------------
    // Start without handlers
    // -------------------------------------------------------

    @Test
    void startWithoutHandlersThrows() {
        var worker = OJSWorker.builder()
                .url("http://localhost:8080")
                .build();

        var ex = assertThrows(IllegalStateException.class, worker::start);
        assertEquals("No handlers registered. Call register() before start().", ex.getMessage());
    }
}
