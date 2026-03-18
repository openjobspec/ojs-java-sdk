package org.openjobspec.ojs.attest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OJS attestation types and attestor implementations.
 */
class AttestorTest {

    private static AttestInput sampleInput() {
        return new AttestInput(
                "01912f4e-fd1a-7000-8000-000000000001",
                "payments.charge",
                "sha256:abc123",
                "sha256:def456",
                Instant.parse("2025-01-15T10:00:00Z")
        );
    }

    @Nested
    class NoneAttestorTests {

        @Test
        void attestReturnsEmptyResult() {
            var attestor = new NoneAttestor();
            assertEquals("none", attestor.name());

            AttestResult result = attestor.attest(sampleInput());

            assertNotNull(result);
            assertNotNull(result.quote());
            assertEquals(Quote.TYPE_NONE, result.quote().type());
            assertEquals(0, result.quote().evidence().length);
        }

        @Test
        void verifyAlwaysSucceeds() {
            var attestor = new NoneAttestor();
            var receipt = new Receipt(
                    "job-1",
                    new Quote(Quote.TYPE_NONE, new byte[0], "", Instant.now()),
                    null,
                    null,
                    new Signature(Signature.ALG_ED25519, "", ""),
                    Instant.now()
            );
            assertDoesNotThrow(() -> attestor.verify(receipt));
        }
    }

    @Nested
    class PQCOnlyAttestorTests {

        @Test
        void attestAndVerifyRoundTrip() throws AttestException {
            var attestor = new PQCOnlyAttestor("test-key-1");
            assertEquals("pqc-only", attestor.name());

            AttestInput input = sampleInput();
            AttestResult result = attestor.attest(input);

            assertNotNull(result);
            assertNotNull(result.quote());
            assertEquals(Quote.TYPE_PQC_ONLY, result.quote().type());
            assertNotNull(result.signature());
            assertEquals(Signature.ALG_ED25519, result.signature().algorithm());
            assertEquals("test-key-1", result.signature().keyId());
            assertFalse(result.signature().value().isEmpty());

            var receipt = new Receipt(
                    input.jobId(),
                    result.quote(),
                    result.jurisdiction(),
                    result.modelFingerprint(),
                    result.signature(),
                    Instant.now()
            );
            assertDoesNotThrow(() -> attestor.verify(receipt));
        }

        @Test
        void verifyRejectsNullQuote() throws AttestException {
            var attestor = new PQCOnlyAttestor("test-key-2");
            var receipt = new Receipt(
                    "job-1",
                    null,
                    null,
                    null,
                    new Signature(Signature.ALG_ED25519, "abc", "k"),
                    Instant.now()
            );
            assertThrows(AttestException.class, () -> attestor.verify(receipt));
        }

        @Test
        void verifyRejectsTamperedSignature() throws AttestException {
            var attestor = new PQCOnlyAttestor("test-key-3");
            AttestResult result = attestor.attest(sampleInput());

            var receipt = new Receipt(
                    "job-1",
                    result.quote(),
                    null,
                    null,
                    new Signature(Signature.ALG_ED25519, "dGFtcGVyZWQ=", "test-key-3"),
                    Instant.now()
            );
            assertThrows(AttestException.class, () -> attestor.verify(receipt));
        }
    }

    @Nested
    class RecordValidationTests {

        @Test
        void attestInputRequiresNonNullFields() {
            assertThrows(NullPointerException.class, () ->
                    new AttestInput(null, "type", "hash", "hash"));
            assertThrows(NullPointerException.class, () ->
                    new AttestInput("id", null, "hash", "hash"));
        }

        @Test
        void attestInputDefaultsTimestamp() {
            var input = new AttestInput("id", "type", "hash", "hash");
            assertNotNull(input.timestamp());
        }

        @Test
        void receiptRequiresNonNullFields() {
            assertThrows(NullPointerException.class, () ->
                    new Receipt(null, null, null, null,
                            new Signature("ed25519", "v", "k"), Instant.now()));
        }

        @Test
        void signatureRequiresNonNullFields() {
            assertThrows(NullPointerException.class, () ->
                    new Signature(null, "v", "k"));
            assertThrows(NullPointerException.class, () ->
                    new Signature("ed25519", null, "k"));
            assertThrows(NullPointerException.class, () ->
                    new Signature("ed25519", "v", null));
        }
    }
}
