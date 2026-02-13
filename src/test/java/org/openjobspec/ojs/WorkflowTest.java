package org.openjobspec.ojs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {

    // ------------------------------------------------------------------ //
    //  Step factory
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Step factory")
    class StepFactory {

        @Test
        @DisplayName("step(type, args) creates step with correct type and args")
        void stepWithTypeAndArgs() {
            var args = Map.<String, Object>of("key", "value");
            var step = Workflow.step("email.send", args);

            assertEquals("email.send", step.type());
            assertEquals(args, step.args());
        }

        @Test
        @DisplayName("step with null args defaults to empty map")
        void stepWithNullArgs() {
            var step = Workflow.step("email.send", null);
            assertNotNull(step.args());
            assertTrue(step.args().isEmpty());
        }

        @Test
        @DisplayName("step with null type throws NullPointerException")
        void stepWithNullType() {
            assertThrows(NullPointerException.class,
                    () -> Workflow.step(null, Map.of()));
        }

        @Test
        @DisplayName("step has null queue by default")
        void stepDefaultQueue() {
            var step = Workflow.step("email.send", Map.of());
            assertNull(step.queue());
        }

        @Test
        @DisplayName("step has null retry by default")
        void stepDefaultRetry() {
            var step = Workflow.step("email.send", Map.of());
            assertNull(step.retry());
        }

        @Test
        @DisplayName("step has 0 priority by default")
        void stepDefaultPriority() {
            var step = Workflow.step("email.send", Map.of());
            assertEquals(0, step.priority());
        }

        @Test
        @DisplayName("step(type, args, queue) sets queue")
        void stepWithQueue() {
            var step = Workflow.step("email.send", Map.of("to", "a@b.com"), "high-priority");
            assertEquals("high-priority", step.queue());
        }
    }

    // ------------------------------------------------------------------ //
    //  Chain workflow
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Chain workflow")
    class ChainWorkflow {

        @Test
        @DisplayName("chain has type 'chain'")
        void chainType() {
            var def = Workflow.chain("test-chain",
                    Workflow.step("step.a", Map.of()),
                    Workflow.step("step.b", Map.of()));
            assertEquals("chain", def.type());
        }

        @Test
        @DisplayName("chain preserves name")
        void chainName() {
            var def = Workflow.chain("order-processing",
                    Workflow.step("step.a", Map.of()));
            assertEquals("order-processing", def.name());
        }

        @Test
        @DisplayName("chain preserves all steps")
        void chainSteps() {
            var s1 = Workflow.step("step.a", Map.of());
            var s2 = Workflow.step("step.b", Map.of());
            var s3 = Workflow.step("step.c", Map.of());
            var def = Workflow.chain("c", s1, s2, s3);
            assertEquals(3, def.steps().size());
        }

        @Test
        @DisplayName("chain has no callbacks")
        void chainNoCallbacks() {
            var def = Workflow.chain("c", Workflow.step("step.a", Map.of()));
            assertNull(def.callbacks());
        }

        @Test
        @DisplayName("chain wire format has sequential depends_on")
        void chainWireFormat() {
            var def = Workflow.chain("seq",
                    Workflow.step("validate", Map.of("id", "123")),
                    Workflow.step("process", Map.of()),
                    Workflow.step("notify", Map.of()));

            var wire = def.toWire();
            assertEquals("seq", wire.get("name"));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) wire.get("steps");
            assertEquals(3, steps.size());

            // First step: no depends_on
            assertEquals("step_0", steps.get(0).get("id"));
            assertEquals("validate", steps.get(0).get("type"));
            assertFalse(steps.get(0).containsKey("depends_on"));

            // Second step: depends on first
            assertEquals("step_1", steps.get(1).get("id"));
            assertEquals("process", steps.get(1).get("type"));
            assertEquals(List.of("step_0"), steps.get(1).get("depends_on"));

            // Third step: depends on second
            assertEquals("step_2", steps.get(2).get("id"));
            assertEquals("notify", steps.get(2).get("type"));
            assertEquals(List.of("step_1"), steps.get(2).get("depends_on"));
        }

        @Test
        @DisplayName("chain wire format includes args as single-element list")
        void chainWireArgsFormat() {
            var args = Map.<String, Object>of("order_id", "ord_123");
            var def = Workflow.chain("c", Workflow.step("validate", args));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");
            assertEquals(List.of(args), steps.get(0).get("args"));
        }

        @Test
        @DisplayName("chain with single step has no depends_on")
        void chainSingleStep() {
            var def = Workflow.chain("c", Workflow.step("only.step", Map.of()));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");
            assertEquals(1, steps.size());
            assertFalse(steps.get(0).containsKey("depends_on"));
        }

        @Test
        @DisplayName("chain wire format includes queue in options when set")
        void chainStepWithQueue() {
            var def = Workflow.chain("c",
                    Workflow.step("step.a", Map.of(), "critical"));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");

            @SuppressWarnings("unchecked")
            var opts = (Map<String, Object>) steps.get(0).get("options");
            assertNotNull(opts);
            assertEquals("critical", opts.get("queue"));
        }
    }

    // ------------------------------------------------------------------ //
    //  Group workflow
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Group workflow")
    class GroupWorkflow {

        @Test
        @DisplayName("group has type 'group'")
        void groupType() {
            var def = Workflow.group("parallel-tasks",
                    Workflow.step("export.csv", Map.of()),
                    Workflow.step("export.pdf", Map.of()));
            assertEquals("group", def.type());
        }

        @Test
        @DisplayName("group preserves name")
        void groupName() {
            var def = Workflow.group("multi-export",
                    Workflow.step("export.csv", Map.of()));
            assertEquals("multi-export", def.name());
        }

        @Test
        @DisplayName("group has no callbacks")
        void groupNoCallbacks() {
            var def = Workflow.group("g", Workflow.step("step.a", Map.of()));
            assertNull(def.callbacks());
        }

        @Test
        @DisplayName("group wire format has NO depends_on on any step")
        void groupWireNoDependsOn() {
            var def = Workflow.group("parallel",
                    Workflow.step("task.a", Map.of("report", "rpt_1")),
                    Workflow.step("task.b", Map.of("report", "rpt_2")),
                    Workflow.step("task.c", Map.of("report", "rpt_3")));

            var wire = def.toWire();

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) wire.get("steps");
            assertEquals(3, steps.size());

            for (var step : steps) {
                assertFalse(step.containsKey("depends_on"),
                        "Group step should not have depends_on: " + step.get("id"));
            }
        }

        @Test
        @DisplayName("group wire step IDs are sequential")
        void groupWireStepIds() {
            var def = Workflow.group("g",
                    Workflow.step("a", Map.of()),
                    Workflow.step("b", Map.of()));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");
            assertEquals("step_0", steps.get(0).get("id"));
            assertEquals("step_1", steps.get(1).get("id"));
        }
    }

    // ------------------------------------------------------------------ //
    //  Batch workflow
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Batch workflow")
    class BatchWorkflow {

        @Test
        @DisplayName("batch has type 'batch'")
        void batchType() {
            var def = Workflow.batch("bulk",
                    Workflow.callbacks().onComplete(Workflow.step("report", Map.of())),
                    Workflow.step("job.a", Map.of()));
            assertEquals("batch", def.type());
        }

        @Test
        @DisplayName("batch preserves name")
        void batchName() {
            var def = Workflow.batch("bulk-email",
                    Workflow.callbacks().onComplete(Workflow.step("report", Map.of())),
                    Workflow.step("email.send", Map.of()));
            assertEquals("bulk-email", def.name());
        }

        @Test
        @DisplayName("batch has callbacks")
        void batchHasCallbacks() {
            var def = Workflow.batch("b",
                    Workflow.callbacks().onComplete(Workflow.step("done", Map.of())),
                    Workflow.step("work", Map.of()));
            assertNotNull(def.callbacks());
        }

        @Test
        @DisplayName("batch wire format: job steps have no depends_on")
        void batchJobStepsNoDependsOn() {
            var def = Workflow.batch("b",
                    Workflow.callbacks().onComplete(Workflow.step("report", Map.of())),
                    Workflow.step("job.a", Map.of()),
                    Workflow.step("job.b", Map.of()));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");

            // First two entries are the actual job steps
            assertFalse(steps.get(0).containsKey("depends_on"));
            assertFalse(steps.get(1).containsKey("depends_on"));
        }

        @Test
        @DisplayName("batch wire format: onComplete callback depends on all job steps")
        void batchOnCompleteCallback() {
            var def = Workflow.batch("b",
                    Workflow.callbacks().onComplete(Workflow.step("batch.report", Map.of())),
                    Workflow.step("email.send", Map.of("to", "user1@test.com")),
                    Workflow.step("email.send", Map.of("to", "user2@test.com")));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");

            // 2 job steps + 1 callback = 3 wire steps
            assertEquals(3, steps.size());

            var callbackStep = steps.get(2);
            assertEquals("on_complete", callbackStep.get("id"));
            assertEquals("batch.report", callbackStep.get("type"));
            assertEquals(List.of("step_0", "step_1"), callbackStep.get("depends_on"));
        }

        @Test
        @DisplayName("batch wire format: onFailure callback depends on all job steps")
        void batchOnFailureCallback() {
            var def = Workflow.batch("b",
                    Workflow.callbacks().onFailure(Workflow.step("alert", Map.of())),
                    Workflow.step("process.a", Map.of()),
                    Workflow.step("process.b", Map.of()),
                    Workflow.step("process.c", Map.of()));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");

            // 3 jobs + 1 callback
            assertEquals(4, steps.size());

            var callbackStep = steps.get(3);
            assertEquals("on_failure", callbackStep.get("id"));
            assertEquals("alert", callbackStep.get("type"));
            assertEquals(List.of("step_0", "step_1", "step_2"), callbackStep.get("depends_on"));
        }

        @Test
        @DisplayName("batch with multiple callbacks includes all in wire format")
        void batchMultipleCallbacks() {
            var def = Workflow.batch("b",
                    Workflow.callbacks()
                            .onComplete(Workflow.step("report.all", Map.of()))
                            .onSuccess(Workflow.step("notify.success", Map.of()))
                            .onFailure(Workflow.step("notify.failure", Map.of())),
                    Workflow.step("task.a", Map.of()));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");

            // 1 job step + 3 callbacks = 4 wire steps
            assertEquals(4, steps.size());

            assertEquals("step_0", steps.get(0).get("id"));
            assertEquals("on_complete", steps.get(1).get("id"));
            assertEquals("on_success", steps.get(2).get("id"));
            assertEquals("on_failure", steps.get(3).get("id"));

            // All callbacks depend on all job steps
            for (int i = 1; i <= 3; i++) {
                assertEquals(List.of("step_0"), steps.get(i).get("depends_on"),
                        "Callback at index " + i + " should depend on step_0");
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Callbacks builder
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("CallbacksBuilder")
    class CallbacksBuilderTest {

        @Test
        @DisplayName("callbacks() returns a CallbacksBuilder")
        void callbacksFactory() {
            assertNotNull(Workflow.callbacks());
        }

        @Test
        @DisplayName("build with onComplete only succeeds")
        void buildWithOnCompleteOnly() {
            var cb = Workflow.callbacks()
                    .onComplete(Workflow.step("done", Map.of()))
                    .build();
            assertNotNull(cb.onComplete());
            assertNull(cb.onSuccess());
            assertNull(cb.onFailure());
        }

        @Test
        @DisplayName("build with onSuccess only succeeds")
        void buildWithOnSuccessOnly() {
            var cb = Workflow.callbacks()
                    .onSuccess(Workflow.step("success", Map.of()))
                    .build();
            assertNull(cb.onComplete());
            assertNotNull(cb.onSuccess());
            assertNull(cb.onFailure());
        }

        @Test
        @DisplayName("build with onFailure only succeeds")
        void buildWithOnFailureOnly() {
            var cb = Workflow.callbacks()
                    .onFailure(Workflow.step("fail", Map.of()))
                    .build();
            assertNull(cb.onComplete());
            assertNull(cb.onSuccess());
            assertNotNull(cb.onFailure());
        }

        @Test
        @DisplayName("build with no callbacks throws IllegalStateException")
        void buildWithNoCallbacksThrows() {
            var builder = Workflow.callbacks();
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        @DisplayName("build with all callbacks set succeeds")
        void buildWithAllCallbacks() {
            var cb = Workflow.callbacks()
                    .onComplete(Workflow.step("complete", Map.of()))
                    .onSuccess(Workflow.step("success", Map.of()))
                    .onFailure(Workflow.step("failure", Map.of()))
                    .build();
            assertAll(
                    () -> assertNotNull(cb.onComplete()),
                    () -> assertNotNull(cb.onSuccess()),
                    () -> assertNotNull(cb.onFailure())
            );
        }
    }

    // ------------------------------------------------------------------ //
    //  Wire format details
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Wire format details")
    class WireFormatDetails {

        @Test
        @DisplayName("wire format contains name at top level")
        void wireContainsName() {
            var def = Workflow.chain("my-workflow", Workflow.step("a", Map.of()));
            var wire = def.toWire();
            assertEquals("my-workflow", wire.get("name"));
        }

        @Test
        @DisplayName("wire format contains steps list at top level")
        void wireContainsStepsList() {
            var def = Workflow.group("g", Workflow.step("a", Map.of()));
            var wire = def.toWire();
            assertInstanceOf(List.class, wire.get("steps"));
        }

        @Test
        @DisplayName("wire step contains id, type, and args")
        void wireStepHasRequiredFields() {
            var args = Map.<String, Object>of("x", 42);
            var def = Workflow.group("g", Workflow.step("my.type", args));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");
            var step = steps.get(0);

            assertAll(
                    () -> assertEquals("step_0", step.get("id")),
                    () -> assertEquals("my.type", step.get("type")),
                    () -> assertEquals(List.of(args), step.get("args"))
            );
        }

        @Test
        @DisplayName("wire step without queue has no options key")
        void wireStepNoQueueNoOptions() {
            var def = Workflow.group("g", Workflow.step("a", Map.of()));

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) def.toWire().get("steps");
            assertFalse(steps.get(0).containsKey("options"));
        }

        @Test
        @DisplayName("empty chain produces empty steps list")
        void emptyChain() {
            var def = Workflow.chain("empty");
            var wire = def.toWire();

            @SuppressWarnings("unchecked")
            var steps = (List<Map<String, Object>>) wire.get("steps");
            assertTrue(steps.isEmpty());
        }
    }
}
