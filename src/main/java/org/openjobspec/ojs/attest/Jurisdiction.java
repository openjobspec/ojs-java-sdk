package org.openjobspec.ojs.attest;

import java.util.Objects;

/**
 * Where the attestation was produced.
 *
 * @param region     cloud region (e.g. "us-east-1")
 * @param datacenter datacenter identifier
 * @param prover     identity of the attesting prover
 */
public record Jurisdiction(
        String region,
        String datacenter,
        String prover
) {
    public Jurisdiction {
        Objects.requireNonNull(region, "region must not be null");
    }
}
