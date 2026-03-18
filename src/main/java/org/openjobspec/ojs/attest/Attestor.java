package org.openjobspec.ojs.attest;

/**
 * Interface for all OJS attestation implementations.
 *
 * <p>An Attestor produces and verifies attestation receipts that bind job
 * inputs/outputs to a measurement of the executing environment. See the
 * M1 Verifiable Compute specification for details.
 */
public interface Attestor {

    /**
     * Returns the name of this attestor (e.g. "none", "pqc-only", "aws-nitro").
     */
    String name();

    /**
     * Produces an attestation result for the given input.
     *
     * @param input the attestation input envelope
     * @return the attestation result containing quote, signature, and metadata
     * @throws AttestException if attestation fails
     */
    AttestResult attest(AttestInput input) throws AttestException;

    /**
     * Verifies a previously issued attestation receipt.
     *
     * @param receipt the receipt to verify
     * @throws AttestException if verification fails
     */
    void verify(Receipt receipt) throws AttestException;
}
