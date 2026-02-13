import org.openjobspec.ojs.*;

import java.time.Duration;
import java.util.*;

/**
 * Demonstrates basic OJS client usage: enqueuing jobs with various options,
 * retrieving job status, and checking server health.
 *
 * <p>Prerequisites: An OJS-compatible server running at http://localhost:8080.
 */
public class BasicEnqueue {

    public static void main(String[] args) {
        // Build a client pointing at the OJS server
        var client = OJSClient.builder()
                .url("http://localhost:8080")
                .build();

        // --- Simple enqueue (returns the created Job directly) ---
        var job = client.enqueue("email.send", Map.of("to", "user@example.com"));
        System.out.println("Created job: " + job.id());

        // --- Enqueue with options using the fluent builder ---
        // Using the Object overload returns a JobRequest that supports chaining.
        // Call .send() at the end to submit the job to the server.
        var scheduled = client.enqueue("report.generate", (Object) Map.of("id", 42))
                .queue("reports")
                .delay(Duration.ofMinutes(5))
                .retry(RetryPolicy.builder().maxAttempts(5).build())
                .unique(UniquePolicy.builder()
                        .key(List.of("id"))
                        .period(Duration.ofHours(1))
                        .build())
                .send();
        System.out.println("Scheduled job: " + scheduled.id() + " state: " + scheduled.state());

        // --- Get job status ---
        var status = client.getJob(job.id());
        System.out.println("Job state: " + status.state());

        // --- Cancel a job ---
        var cancelled = client.cancelJob(scheduled.id());
        System.out.println("Cancelled job: " + cancelled.id() + " state: " + cancelled.state());

        // --- Health check ---
        var health = client.health();
        System.out.println("Server health: " + health);
    }
}
