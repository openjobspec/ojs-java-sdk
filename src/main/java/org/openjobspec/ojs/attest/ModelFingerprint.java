package org.openjobspec.ojs.attest;

import java.util.Objects;

/**
 * ML model identity for auditability.
 *
 * @param sha256      SHA-256 hash of the model weights
 * @param registryUrl URL of the model in a registry
 */
public record ModelFingerprint(
        String sha256,
        String registryUrl
) {
    public ModelFingerprint {
        Objects.requireNonNull(sha256, "sha256 must not be null");
    }
}
