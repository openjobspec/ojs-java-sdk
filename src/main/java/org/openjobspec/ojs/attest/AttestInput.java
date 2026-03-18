package org.openjobspec.ojs.attest;

import java.time.Instant;
import java.util.Objects;

/**
 * Input envelope for attestation. Contains the identifiers and hashes
 * that bind a job execution to its attestation.
 *
 * @param jobId      the job identifier (UUIDv7)
 * @param jobType    the job type (e.g. "payments.charge")
 * @param argsHash   SHA-256 hash of the job arguments
 * @param resultHash SHA-256 hash of the job result
 * @param timestamp  when the attestation was requested
 */
public record AttestInput(
        String jobId,
        String jobType,
        String argsHash,
        String resultHash,
        Instant timestamp
) {
    public AttestInput {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(jobType, "jobType must not be null");
        Objects.requireNonNull(argsHash, "argsHash must not be null");
        Objects.requireNonNull(resultHash, "resultHash must not be null");
        if (timestamp == null) timestamp = Instant.now();
    }

    /**
     * Convenience constructor that uses the current time as the timestamp.
     */
    public AttestInput(String jobId, String jobType, String argsHash, String resultHash) {
        this(jobId, jobType, argsHash, resultHash, Instant.now());
    }
}
