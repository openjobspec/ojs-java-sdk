package org.openjobspec.ojs;

/**
 * Functional interface for handling OJS events.
 *
 * <p>Register handlers with {@link EventEmitter#on(String, EventHandler)}
 * to receive notifications when job state transitions or other lifecycle
 * events occur.
 *
 * <p>Example:
 * <pre>{@code
 * emitter.on(Event.JOB_COMPLETED, event -> {
 *     System.out.printf("Job %s completed%n", event.jobId());
 * });
 * }</pre>
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Handle an OJS event.
     *
     * @param event the event to handle
     */
    void handle(Event event);
}
