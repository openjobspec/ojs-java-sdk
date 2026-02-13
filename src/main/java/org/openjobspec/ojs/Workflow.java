package org.openjobspec.ojs;

import java.util.*;

/**
 * Workflow primitives: chain (sequential), group (parallel), and batch (parallel with callbacks).
 *
 * <pre>{@code
 * // Chain: sequential execution
 * var chain = Workflow.chain("order-processing",
 *     Workflow.step("order.validate", Map.of("order_id", "ord_123")),
 *     Workflow.step("payment.charge", Map.of()),
 *     Workflow.step("notification.send", Map.of())
 * );
 * client.createWorkflow(chain);
 *
 * // Group: parallel execution
 * var group = Workflow.group("multi-export",
 *     Workflow.step("export.csv", Map.of("report_id", "rpt_456")),
 *     Workflow.step("export.pdf", Map.of("report_id", "rpt_456"))
 * );
 *
 * // Batch: parallel with callbacks
 * var batch = Workflow.batch("bulk-email",
 *     Workflow.callbacks()
 *         .onComplete(Workflow.step("batch.report", Map.of()))
 *         .onFailure(Workflow.step("batch.alert", Map.of())),
 *     Workflow.step("email.send", Map.of("to", "user1@example.com")),
 *     Workflow.step("email.send", Map.of("to", "user2@example.com"))
 * );
 * }</pre>
 */
public final class Workflow {

    private Workflow() {}

    /** A step (job) within a workflow. */
    public record Step(
            String type,
            Map<String, Object> args,
            String queue,
            RetryPolicy retry,
            int priority
    ) {
        public Step {
            Objects.requireNonNull(type, "type must not be null");
            if (args == null) args = Map.of();
        }
    }

    /** Batch callback definitions. */
    public record BatchCallbacks(
            Step onComplete,
            Step onSuccess,
            Step onFailure
    ) {}

    /** Batch callbacks builder. */
    public static final class CallbacksBuilder {
        private Step onComplete;
        private Step onSuccess;
        private Step onFailure;

        CallbacksBuilder() {}

        public CallbacksBuilder onComplete(Step step) {
            this.onComplete = step;
            return this;
        }

        public CallbacksBuilder onSuccess(Step step) {
            this.onSuccess = step;
            return this;
        }

        public CallbacksBuilder onFailure(Step step) {
            this.onFailure = step;
            return this;
        }

        public BatchCallbacks build() {
            if (onComplete == null && onSuccess == null && onFailure == null) {
                throw new IllegalStateException("At least one callback must be defined");
            }
            return new BatchCallbacks(onComplete, onSuccess, onFailure);
        }
    }

    /** Workflow definition to be submitted to the server. */
    public record Definition(
            String type,
            String name,
            List<Step> steps,
            BatchCallbacks callbacks
    ) {
        public Definition {
            Objects.requireNonNull(type, "type must not be null");
            if (steps == null) steps = List.of();
        }

        /** Convert to wire format for the HTTP request. */
        Map<String, Object> toWire() {
            var request = new LinkedHashMap<String, Object>();
            request.put("name", name);

            var wireSteps = new ArrayList<Map<String, Object>>();
            var stepIds = new ArrayList<String>();

            for (int i = 0; i < steps.size(); i++) {
                var step = steps.get(i);
                var stepId = "step_" + i;
                stepIds.add(stepId);

                var wireStep = new LinkedHashMap<String, Object>();
                wireStep.put("id", stepId);
                wireStep.put("type", step.type());
                wireStep.put("args", List.of(step.args()));

                // Set dependencies based on workflow type
                if ("chain".equals(type) && i > 0) {
                    wireStep.put("depends_on", List.of(stepIds.get(i - 1)));
                }

                if (step.queue() != null) {
                    var opts = new LinkedHashMap<String, Object>();
                    opts.put("queue", step.queue());
                    wireStep.put("options", opts);
                }

                wireSteps.add(wireStep);
            }

            // Add batch callbacks as dependent steps
            if ("batch".equals(type) && callbacks != null) {
                addCallbackStep(wireSteps, stepIds, callbacks.onComplete(), "on_complete");
                addCallbackStep(wireSteps, stepIds, callbacks.onSuccess(), "on_success");
                addCallbackStep(wireSteps, stepIds, callbacks.onFailure(), "on_failure");
            }

            request.put("steps", wireSteps);
            return request;
        }

        private void addCallbackStep(List<Map<String, Object>> wireSteps,
                                     List<String> dependsOn, Step callback, String callbackId) {
            if (callback == null) return;
            var wireStep = new LinkedHashMap<String, Object>();
            wireStep.put("id", callbackId);
            wireStep.put("type", callback.type());
            wireStep.put("args", List.of(callback.args()));
            wireStep.put("depends_on", new ArrayList<>(dependsOn));
            wireSteps.add(wireStep);
        }
    }

    /** Workflow status returned by the server. */
    public record WorkflowStatus(
            String id,
            String name,
            String state,
            String createdAt,
            String completedAt,
            List<WorkflowStep> steps
    ) {}

    /** Individual step status within a workflow. */
    public record WorkflowStep(
            String id,
            String type,
            String state,
            String jobId,
            List<String> dependsOn,
            String startedAt,
            String completedAt,
            Map<String, Object> result
    ) {}

    // --- Factory Methods ---

    /** Create a step definition. */
    public static Step step(String type, Map<String, Object> args) {
        return new Step(type, args, null, null, 0);
    }

    /** Create a step with a specific queue. */
    public static Step step(String type, Map<String, Object> args, String queue) {
        return new Step(type, args, queue, null, 0);
    }

    /** Create a callbacks builder. */
    public static CallbacksBuilder callbacks() {
        return new CallbacksBuilder();
    }

    /** Create a chain (sequential) workflow definition. */
    public static Definition chain(String name, Step... steps) {
        return new Definition("chain", name, List.of(steps), null);
    }

    /** Create a group (parallel) workflow definition. */
    public static Definition group(String name, Step... jobs) {
        return new Definition("group", name, List.of(jobs), null);
    }

    /** Create a batch (parallel with callbacks) workflow definition. */
    public static Definition batch(String name, CallbacksBuilder callbacks, Step... jobs) {
        return new Definition("batch", name, List.of(jobs), callbacks.build());
    }

    // --- Response Parsing ---

    @SuppressWarnings("unchecked")
    static WorkflowStatus parseStatus(Map<String, Object> response) {
        var wf = response.containsKey("workflow")
                ? (Map<String, Object>) response.get("workflow")
                : response;

        var stepsList = new ArrayList<WorkflowStep>();
        if (wf.get("steps") instanceof List<?> rawSteps) {
            for (var rawStep : rawSteps) {
                if (rawStep instanceof Map<?, ?> stepMap) {
                    var sm = (Map<String, Object>) stepMap;
                    stepsList.add(new WorkflowStep(
                            str(sm, "id"),
                            str(sm, "type"),
                            str(sm, "state"),
                            str(sm, "job_id"),
                            sm.get("depends_on") instanceof List<?> deps
                                    ? deps.stream().map(Object::toString).toList()
                                    : List.of(),
                            str(sm, "started_at"),
                            str(sm, "completed_at"),
                            sm.get("result") instanceof Map<?, ?> r
                                    ? (Map<String, Object>) r : Map.of()
                    ));
                }
            }
        }

        return new WorkflowStatus(
                str(wf, "id"),
                str(wf, "name"),
                str(wf, "state"),
                str(wf, "created_at"),
                str(wf, "completed_at"),
                stepsList
        );
    }

    private static String str(Map<String, Object> map, String key) {
        var v = map.get(key);
        return v instanceof String s ? s : null;
    }
}
