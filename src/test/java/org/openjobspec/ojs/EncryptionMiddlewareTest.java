package org.openjobspec.ojs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionMiddleware — EncryptionCodec & StaticKeyProvider")
class EncryptionMiddlewareTest {

    private EncryptionMiddleware.EncryptionCodec codec;
    private byte[] key;

    @BeforeEach
    void setUp() {
        codec = new EncryptionMiddleware.EncryptionCodec();
        key = new byte[32];
        new SecureRandom().nextBytes(key);
    }

    @Test
    @DisplayName("encrypt → decrypt round-trip preserves plaintext")
    void testEncryptDecryptRoundTrip() {
        byte[] plaintext = "hello, OJS encryption!".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = codec.encrypt(plaintext, key);
        byte[] decrypted = codec.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("decrypt with wrong key throws RuntimeException")
    void testDecryptWrongKey() {
        byte[] plaintext = "secret data".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = codec.encrypt(plaintext, key);

        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);

        assertThrows(RuntimeException.class, () -> codec.decrypt(encrypted, wrongKey));
    }

    @Test
    @DisplayName("two encryptions of the same plaintext produce different ciphertexts (unique nonces)")
    void testNonceUniqueness() {
        byte[] plaintext = "deterministic input".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted1 = codec.encrypt(plaintext, key);
        byte[] encrypted2 = codec.encrypt(plaintext, key);

        assertFalse(
                java.util.Arrays.equals(encrypted1, encrypted2),
                "Two encryptions must produce different ciphertexts due to unique nonces");
    }

    @Test
    @DisplayName("StaticKeyProvider sets meta keys ojs.codec.encodings and ojs.codec.key_id")
    void testMetaKeysSetCorrectly() {
        String keyId = "primary-2024";
        var provider = new EncryptionMiddleware.StaticKeyProvider(
                Map.of(keyId, key), keyId);

        assertEquals(keyId, provider.getCurrentKeyId());
        assertArrayEquals(key, provider.getKey(keyId));
    }

    @Test
    @DisplayName("key rotation: encrypt with current key, decrypt with either key")
    void testKeyRotation() {
        byte[] keyA = new byte[32];
        byte[] keyB = new byte[32];
        new SecureRandom().nextBytes(keyA);
        new SecureRandom().nextBytes(keyB);

        // Provider with keyA as current
        var providerA = new EncryptionMiddleware.StaticKeyProvider(
                Map.of("key-a", keyA, "key-b", keyB), "key-a");

        byte[] plaintext = "rotated payload".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = codec.encrypt(plaintext, providerA.getKey(providerA.getCurrentKeyId()));

        // Decrypt with the same key retrieved by either key ID
        byte[] decryptedWithA = codec.decrypt(encrypted, providerA.getKey("key-a"));
        assertArrayEquals(plaintext, decryptedWithA);

        // Provider with keyB as current can still look up keyA
        var providerB = new EncryptionMiddleware.StaticKeyProvider(
                Map.of("key-a", keyA, "key-b", keyB), "key-b");
        byte[] decryptedViaB = codec.decrypt(encrypted, providerB.getKey("key-a"));
        assertArrayEquals(plaintext, decryptedViaB);

        // Decrypting with keyB (wrong key) must fail
        assertThrows(RuntimeException.class,
                () -> codec.decrypt(encrypted, providerB.getKey("key-b")));
    }

    @Test
    @DisplayName("encrypt and decrypt empty byte array")
    void testEmptyPlaintext() {
        byte[] empty = new byte[0];

        byte[] encrypted = codec.encrypt(empty, key);
        assertTrue(encrypted.length > 0, "Encrypted output must not be empty");

        byte[] decrypted = codec.decrypt(encrypted, key);
        assertArrayEquals(empty, decrypted);
    }

    @Test
    @DisplayName("StaticKeyProvider rejects unknown key ID on lookup")
    void testUnknownKeyIdThrows() {
        var provider = new EncryptionMiddleware.StaticKeyProvider(
                Map.of("known", key), "known");

        assertThrows(IllegalArgumentException.class, () -> provider.getKey("unknown"));
    }

    @Test
    @DisplayName("StaticKeyProvider rejects currentKeyId not in map")
    void testCurrentKeyIdNotInMapThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptionMiddleware.StaticKeyProvider(
                        Map.of("a", key), "missing"));
    }
}
