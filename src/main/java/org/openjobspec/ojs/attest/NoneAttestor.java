package org.openjobspec.ojs.attest;

/**
 * Default no-op attestor that always returns an empty result.
 *
 * <p>Suitable for development and testing environments where
 * attestation is not required.
 */
public final class NoneAttestor implements Attestor {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public AttestResult attest(AttestInput input) {
        return new AttestResult(
                new Quote(Quote.TYPE_NONE, new byte[0], "", input.timestamp()),
                new Signature(Signature.ALG_ED25519, "", "")
        );
    }

    @Override
    public void verify(Receipt receipt) {
        // No-op: none attestor always succeeds.
    }
}
