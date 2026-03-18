package org.openjobspec.ojs.attest;

/**
 * Result of a successful attestation.
 *
 * @param quote            attestation evidence from the TEE or software layer
 * @param jurisdiction     where the attestation was produced
 * @param modelFingerprint ML model identity for auditability (nullable)
 * @param signature        cryptographic signature over the attestation
 */
public record AttestResult(
        Quote quote,
        Jurisdiction jurisdiction,
        ModelFingerprint modelFingerprint,
        Signature signature
) {
    /**
     * Convenience constructor without model fingerprint.
     */
    public AttestResult(Quote quote, Signature signature) {
        this(quote, null, null, signature);
    }
}
