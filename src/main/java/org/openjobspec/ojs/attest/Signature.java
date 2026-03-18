package org.openjobspec.ojs.attest;

import java.util.Objects;

/**
 * Cryptographic signature over the attestation.
 *
 * @param algorithm signature algorithm (e.g. "ed25519", "ml-dsa-65")
 * @param value     base64-encoded signature value
 * @param keyId     identifier of the signing key
 */
public record Signature(
        String algorithm,
        String value,
        String keyId
) {
    public Signature {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
    }

    /** Common algorithm constants. */
    public static final String ALG_ED25519 = "ed25519";
    public static final String ALG_ML_DSA_65 = "ml-dsa-65";
    public static final String ALG_HYBRID = "hybrid:Ed25519+ML-DSA-65";
}
