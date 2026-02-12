package org.openjobspec.ojs;

/**
 * Functional interface for job handlers. A handler processes a job and returns a result.
 *
 * <p>Handlers are registered with a worker for specific job types:
 * <pre>{@code
 * worker.register("email.send", ctx -> {
 *     var to = (String) ctx.job().args().get("to");
 *     sendEmail(to);
 *     return Map.of("messageId", "...");
 * });
 * }</pre>
 *
 * <p>The return value is stored as the job's result upon successful completion.
 * Returning {@code null} is valid and indicates no result.
 */
@FunctionalInterface
public interface JobHandler {

    /**
     * Handle a job.
     *
     * @param ctx the job context containing the job data and utilities
     * @return the job result (may be null)
     * @throws Exception if the handler fails
     */
    Object handle(JobContext ctx) throws Exception;
}
