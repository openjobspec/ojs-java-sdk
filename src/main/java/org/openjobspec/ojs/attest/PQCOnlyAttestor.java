package org.openjobspec.ojs.attest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Software-only attestor using Ed25519 signing via {@code java.security}.
 *
 * <p>Provides integrity guarantees without requiring hardware TEE support.
 * Uses Ed25519 key pairs generated from the standard Java security provider.
 */
public final class PQCOnlyAttestor implements Attestor {

    private final KeyPair keyPair;
    private final String keyId;

    /**
     * Creates a PQC-only attestor with a fresh Ed25519 key pair.
     *
     * @param keyId identifier for the signing key
     * @throws AttestException if key generation fails
     */
    public PQCOnlyAttestor(String keyId) throws AttestException {
        this.keyId = keyId;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            this.keyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AttestException("Ed25519 not available on this JVM", e);
        }
    }

    /**
     * Creates a PQC-only attestor with a provided key pair.
     *
     * @param keyPair the Ed25519 key pair
     * @param keyId   identifier for the signing key
     */
    public PQCOnlyAttestor(KeyPair keyPair, String keyId) {
        this.keyPair = keyPair;
        this.keyId = keyId;
    }

    @Override
    public String name() {
        return "pqc-only";
    }

    @Override
    public AttestResult attest(AttestInput input) throws AttestException {
        byte[] digest = computeDigest(input);
        byte[] sig = sign(digest);
        String sigB64 = Base64.getEncoder().encodeToString(sig);

        return new AttestResult(
                new Quote(
                        Quote.TYPE_PQC_ONLY,
                        digest,
                        bytesToHex(digest, 16),
                        input.timestamp()
                ),
                new Signature(Signature.ALG_ED25519, sigB64, keyId)
        );
    }

    @Override
    public void verify(Receipt receipt) throws AttestException {
        if (receipt.quote() == null) {
            throw new AttestException("receipt has no quote");
        }
        String sigB64 = receipt.signature().value();
        byte[] sig = Base64.getDecoder().decode(sigB64);
        byte[] evidence = receipt.quote().evidence();

        try {
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(keyPair.getPublic());
            verifier.update(evidence);
            if (!verifier.verify(sig)) {
                throw new AttestException("Ed25519 signature verification failed");
            }
        } catch (GeneralSecurityException e) {
            throw new AttestException("verification error: " + e.getMessage(), e);
        }
    }

    private byte[] sign(byte[] data) throws AttestException {
        try {
            java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
            signer.initSign(keyPair.getPrivate());
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new AttestException("signing error: " + e.getMessage(), e);
        }
    }

    private static byte[] computeDigest(AttestInput input) throws AttestException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(input.argsHash().getBytes(StandardCharsets.UTF_8));
            md.update(input.resultHash().getBytes(StandardCharsets.UTF_8));
            md.update(input.timestamp().toString().getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new AttestException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < Math.min(bytes.length, len); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }
}
