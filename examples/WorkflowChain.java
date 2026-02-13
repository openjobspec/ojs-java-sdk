import org.openjobspec.ojs.*;

import java.util.*;

/**
 * Demonstrates OJS workflow primitives: chain (sequential), group (parallel),
 * and batch (parallel with callbacks).
 *
 * <p>Prerequisites: An OJS-compatible server running at http://localhost:8080.
 */
public class WorkflowChain {

    public static void main(String[] args) {
        var client = OJSClient.builder()
                .url("http://localhost:8080")
                .build();

        // --- Chain workflow: steps execute sequentially ---
        var chain = Workflow.chain("order-processing",
                Workflow.step("order.validate", Map.of("order_id", "ord_123")),
                Workflow.step("payment.charge", Map.of()),
                Workflow.step("inventory.reserve", Map.of()),
                Workflow.step("notification.send", Map.of())
        );
        var chainResult = client.createWorkflow(chain);
        System.out.println("Chain workflow: " + chainResult.id() + " state: " + chainResult.state());

        // --- Group workflow: steps execute in parallel ---
        var group = Workflow.group("multi-export",
                Workflow.step("export.csv", Map.of("report_id", "rpt_456")),
                Workflow.step("export.pdf", Map.of("report_id", "rpt_456")),
                Workflow.step("export.xlsx", Map.of("report_id", "rpt_456"))
        );
        var groupResult = client.createWorkflow(group);
        System.out.println("Group workflow: " + groupResult.id());

        // --- Batch workflow: parallel execution with completion/failure callbacks ---
        var batch = Workflow.batch("bulk-email",
                Workflow.callbacks()
                        .onComplete(Workflow.step("batch.report", Map.of()))
                        .onFailure(Workflow.step("batch.alert", Map.of())),
                Workflow.step("email.send", Map.of("to", "user1@example.com")),
                Workflow.step("email.send", Map.of("to", "user2@example.com")),
                Workflow.step("email.send", Map.of("to", "user3@example.com"))
        );
        var batchResult = client.createWorkflow(batch);
        System.out.println("Batch workflow: " + batchResult.id());

        // --- Check workflow status ---
        var status = client.getWorkflow(chainResult.id());
        System.out.println("Workflow state: " + status.state());
        for (var step : status.steps()) {
            System.out.printf("  Step %s (%s): %s%n", step.id(), step.type(), step.state());
        }
    }
}
