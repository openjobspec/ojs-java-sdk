package org.openjobspec.ojs.attest;

import java.time.Instant;
import java.util.Objects;

/**
 * Attestation evidence produced by the TEE or software layer.
 *
 * @param type     quote type (e.g. "none", "pqc-only", "aws-nitro-v1")
 * @param evidence raw attestation evidence bytes
 * @param nonce    anti-replay nonce
 * @param issuedAt when the quote was produced
 */
public record Quote(
        String type,
        byte[] evidence,
        String nonce,
        Instant issuedAt
) {
    public Quote {
        Objects.requireNonNull(type, "type must not be null");
    }

    /** Common quote type constants. */
    public static final String TYPE_NONE = "none";
    public static final String TYPE_PQC_ONLY = "pqc-only";
    public static final String TYPE_AWS_NITRO = "aws-nitro-v1";
    public static final String TYPE_INTEL_TDX = "intel-tdx-v4";
    public static final String TYPE_AMD_SEV_SNP = "amd-sev-snp-v2";
}
