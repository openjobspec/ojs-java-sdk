package org.openjobspec.ojs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypedJobHandler")
class TypedJobHandlerTest {

    public record EmailArgs(String to, String subject) {}

    public record CountArgs(int count, String name) {}

    @Test
    void deserializesArgsIntoRecord() throws Exception {
        var handler = TypedJobHandler.of(EmailArgs.class, (args, ctx) -> {
            assertEquals("user@test.com", args.to());
            assertEquals("Hello", args.subject());
            return Map.of("sent", true);
        });

        var job = createJobWithArgs(Map.of("to", "user@test.com", "subject", "Hello"));
        var ctx = new JobContext(job, null, null, null);

        var result = handler.handle(ctx);
        assertNotNull(result);
    }

    @Test
    void deserializesNumericArgs() throws Exception {
        var handler = TypedJobHandler.of(CountArgs.class, (args, ctx) -> {
            assertEquals(42, args.count());
            assertEquals("test", args.name());
            return null;
        });

        var job = createJobWithArgs(Map.of("count", 42, "name", "test"));
        var ctx = new JobContext(job, null, null, null);

        handler.handle(ctx);
    }

    @Test
    void ignoresUnknownProperties() throws Exception {
        var handler = TypedJobHandler.of(EmailArgs.class, (args, ctx) -> {
            assertEquals("a@b.com", args.to());
            return null;
        });

        var job = createJobWithArgs(Map.of("to", "a@b.com", "subject", "Hi", "extra_field", "ignored"));
        var ctx = new JobContext(job, null, null, null);

        assertDoesNotThrow(() -> handler.handle(ctx));
    }

    @Test
    void handlesEmptyArgs() throws Exception {
        var handler = TypedJobHandler.of(EmailArgs.class, (args, ctx) -> {
            assertNull(args.to());
            assertNull(args.subject());
            return null;
        });

        var job = createJobWithArgs(Map.of());
        var ctx = new JobContext(job, null, null, null);

        handler.handle(ctx);
    }

    @Test
    void propagatesHandlerException() {
        var handler = TypedJobHandler.of(EmailArgs.class, (args, ctx) -> {
            throw new RuntimeException("handler failed");
        });

        var job = createJobWithArgs(Map.of("to", "a@b.com"));
        var ctx = new JobContext(job, null, null, null);

        var ex = assertThrows(RuntimeException.class, () -> handler.handle(ctx));
        assertEquals("handler failed", ex.getMessage());
    }

    private static Job createJobWithArgs(Map<String, Object> argsMap) {
        return new Job(
                Job.SPEC_VERSION, "test-id", "test.type", "default",
                List.of(argsMap), Map.of(), 0, 0, null, null,
                null, null, null,
                "running", 1, null, null, null, null, null, null, null
        );
    }
}
