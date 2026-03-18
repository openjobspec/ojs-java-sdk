package org.openjobspec.ojs.attest;

/**
 * Checked exception thrown when attestation or verification fails.
 */
public class AttestException extends Exception {

    public AttestException(String message) {
        super(message);
    }

    public AttestException(String message, Throwable cause) {
        super(message, cause);
    }
}
