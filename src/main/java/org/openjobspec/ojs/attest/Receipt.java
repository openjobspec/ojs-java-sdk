package org.openjobspec.ojs.attest;

import java.time.Instant;
import java.util.Objects;

/**
 * Bundle a verifier needs to check an attestation.
 *
 * @param jobId            the job that was attested
 * @param quote            attestation evidence
 * @param jurisdiction     where the attestation was produced
 * @param modelFingerprint ML model identity (nullable)
 * @param signature        cryptographic signature over the attestation
 * @param issuedAt         when the receipt was issued
 */
public record Receipt(
        String jobId,
        Quote quote,
        Jurisdiction jurisdiction,
        ModelFingerprint modelFingerprint,
        Signature signature,
        Instant issuedAt
) {
    public Receipt {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    }
}
