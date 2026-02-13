package org.openjobspec.ojs;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Context passed to job handlers and middleware during job execution.
 *
 * <p>Provides access to the job being processed, workflow context,
 * and utilities for setting results and sending heartbeats.
 */
public final class JobContext {

    private final Job job;
    private final String workflowId;
    private final Map<String, Object> parentResults;
    private final AtomicReference<Object> resultRef;
    private final HeartbeatSender heartbeatSender;
    private volatile boolean cancelled;

    JobContext(Job job, String workflowId, Map<String, Object> parentResults,
              HeartbeatSender heartbeatSender) {
        this.job = job;
        this.workflowId = workflowId;
        this.parentResults = parentResults != null ? parentResults : Map.of();
        this.resultRef = new AtomicReference<>();
        this.heartbeatSender = heartbeatSender;
    }

    /** The job being processed. */
    public Job job() {
        return job;
    }

    /** Current attempt number (1-indexed). */
    public int attempt() {
        return job.attempt();
    }

    /** The queue this job was fetched from. */
    public String queue() {
        return job.queue();
    }

    /** Workflow ID if this job is part of a workflow, null otherwise. */
    public String workflowId() {
        return workflowId;
    }

    /** Results from parent jobs in a chain workflow. */
    public Map<String, Object> parentResults() {
        return parentResults;
    }

    /**
     * Set the result of this job execution. The result will be stored
     * on the job upon successful completion.
     */
    public void setResult(Object result) {
        resultRef.set(result);
    }

    /** Get the current result value. */
    Object getResult() {
        return resultRef.get();
    }

    /**
     * Send a heartbeat to extend the visibility timeout for long-running jobs.
     * This prevents the job from being reclaimed by another worker.
     *
     * @throws OJSError.OJSException if the heartbeat fails
     */
    public void heartbeat() {
        if (heartbeatSender != null) {
            heartbeatSender.sendHeartbeat(job.id());
        }
    }

    /** Check if this job has been cancelled. */
    public boolean isCancelled() {
        return cancelled;
    }

    void markCancelled() {
        this.cancelled = true;
    }

    /** Functional interface for heartbeat sending. */
    @FunctionalInterface
    interface HeartbeatSender {
        void sendHeartbeat(String jobId);
    }
}
