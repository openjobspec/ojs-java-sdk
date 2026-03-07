package org.openjobspec.ojs;

import org.openjobspec.ojs.transport.Json;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Provides AES-256-GCM encryption and decryption middleware for OJS jobs.
 *
 * <p>Encryption middleware serializes a job's {@code args} to JSON, encrypts the
 * bytes with AES-256-GCM, and replaces the args with a single Base64-encoded
 * ciphertext element. Decryption middleware reverses the process, restoring the
 * original args before the job handler executes.
 *
 * <p>Usage example:
 * <pre>{@code
 * var keys = new EncryptionMiddleware.StaticKeyProvider(
 *     Map.of("key-2024", aes256Key), "key-2024");
 * var codec = new EncryptionMiddleware.EncryptionCodec();
 *
 * // Client side: encrypt before enqueue
 * client.use(EncryptionMiddleware.encryptionMiddleware(codec, keys));
 *
 * // Worker side: decrypt before handler
 * worker.use(EncryptionMiddleware.decryptionMiddleware(codec, keys));
 * }</pre>
 */
public final class EncryptionMiddleware {

    private EncryptionMiddleware() {}

    // -------------------------------------------------------------------------
    // KeyProvider
    // -------------------------------------------------------------------------

    /**
     * Provides encryption keys by identifier. Implementations may fetch keys
     * from a vault, environment variables, or any secure key store.
     */
    public interface KeyProvider {

        /**
         * Returns the raw key bytes for the given key identifier.
         *
         * @param keyId the key identifier
         * @return the raw AES key bytes (must be 32 bytes for AES-256)
         * @throws IllegalArgumentException if the key ID is unknown
         */
        byte[] getKey(String keyId);

        /**
         * Returns the identifier of the key that should be used for encryption.
         *
         * @return the current key identifier
         */
        String getCurrentKeyId();
    }

    // -------------------------------------------------------------------------
    // StaticKeyProvider
    // -------------------------------------------------------------------------

    /**
     * A {@link KeyProvider} backed by an in-memory map of keys.
     * Suitable for testing and simple deployments where keys are loaded at startup.
     */
    public static final class StaticKeyProvider implements KeyProvider {

        private final Map<String, byte[]> keys;
        private final String currentKeyId;

        /**
         * Creates a new static key provider.
         *
         * @param keys         a map of key identifiers to raw AES key bytes
         * @param currentKeyId the identifier of the key used for new encryptions
         * @throws NullPointerException     if any argument is null
         * @throws IllegalArgumentException if {@code currentKeyId} is not present in {@code keys}
         */
        public StaticKeyProvider(Map<String, byte[]> keys, String currentKeyId) {
            Objects.requireNonNull(keys, "keys must not be null");
            Objects.requireNonNull(currentKeyId, "currentKeyId must not be null");
            if (!keys.containsKey(currentKeyId)) {
                throw new IllegalArgumentException(
                        "currentKeyId '" + currentKeyId + "' not found in keys map");
            }
            // Defensive copy — keys are stored by reference (byte[] is mutable)
            this.keys = new HashMap<>(keys);
            this.currentKeyId = currentKeyId;
        }

        @Override
        public byte[] getKey(String keyId) {
            byte[] key = keys.get(keyId);
            if (key == null) {
                throw new IllegalArgumentException("Unknown key ID: " + keyId);
            }
            return key;
        }

        @Override
        public String getCurrentKeyId() {
            return currentKeyId;
        }
    }

    // -------------------------------------------------------------------------
    // EncryptionCodec
    // -------------------------------------------------------------------------

    /**
     * AES-256-GCM encryption codec. Thread-safe; each operation generates a
     * unique 12-byte nonce via {@link SecureRandom}.
     *
     * <p>Wire format: {@code nonce (12 bytes) || ciphertext || GCM auth tag (16 bytes)}.
     */
    public static final class EncryptionCodec {

        private static final String ALGORITHM = "AES/GCM/NoPadding";
        private static final int NONCE_LENGTH = 12;
        private static final int TAG_LENGTH_BITS = 128;

        private final SecureRandom secureRandom = new SecureRandom();

        /**
         * Encrypts plaintext with AES-256-GCM.
         *
         * @param plaintext the data to encrypt
         * @param key       the 32-byte AES key
         * @return nonce (12 bytes) prepended to the ciphertext with appended GCM auth tag
         * @throws RuntimeException wrapping any JCE exception
         */
        public byte[] encrypt(byte[] plaintext, byte[] key) {
            try {
                byte[] nonce = new byte[NONCE_LENGTH];
                secureRandom.nextBytes(nonce);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(TAG_LENGTH_BITS, nonce));

                byte[] ciphertext = cipher.doFinal(plaintext);

                byte[] result = new byte[NONCE_LENGTH + ciphertext.length];
                System.arraycopy(nonce, 0, result, 0, NONCE_LENGTH);
                System.arraycopy(ciphertext, 0, result, NONCE_LENGTH, ciphertext.length);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Encryption failed", e);
            }
        }

        /**
         * Decrypts data previously produced by {@link #encrypt(byte[], byte[])}.
         *
         * @param nonceAndCiphertext the nonce-prefixed ciphertext (as returned by encrypt)
         * @param key                the 32-byte AES key
         * @return the original plaintext
         * @throws IllegalArgumentException if the input is shorter than the nonce
         * @throws RuntimeException         wrapping any JCE exception (including auth tag mismatch)
         */
        public byte[] decrypt(byte[] nonceAndCiphertext, byte[] key) {
            try {
                if (nonceAndCiphertext.length < NONCE_LENGTH) {
                    throw new IllegalArgumentException(
                            "Input too short: expected at least " + NONCE_LENGTH + " bytes");
                }

                byte[] nonce = Arrays.copyOfRange(nonceAndCiphertext, 0, NONCE_LENGTH);
                byte[] ciphertext = Arrays.copyOfRange(
                        nonceAndCiphertext, NONCE_LENGTH, nonceAndCiphertext.length);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(TAG_LENGTH_BITS, nonce));

                return cipher.doFinal(ciphertext);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Decryption failed", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Middleware factories
    // -------------------------------------------------------------------------

    // OJS codec spec meta keys.
    private static final String META_ENCODINGS = "ojs.codec.encodings";
    private static final String META_KEY_ID = "ojs.codec.key_id";
    private static final String ENCODING_ENCRYPTED = "binary/encrypted";

    // Legacy meta keys (pre-spec) — checked during decryption for backward compat.
    private static final String LEGACY_META_ENCRYPTED = "ojs.encryption.encrypted";
    private static final String LEGACY_META_KEY_ID = "ojs.encryption.key_id";

    /**
     * Returns a {@link Middleware} that encrypts the job's {@code args} before
     * passing the job down the middleware chain.
     *
     * <p>The middleware serializes args to JSON, encrypts the bytes with the
     * current key, Base64-encodes the result, and sets {@code args} to
     * {@code [base64Ciphertext]}. Encryption metadata is stored in the job's
     * {@code meta} map.
     *
     * @param codec the AES-256-GCM codec
     * @param keys  the key provider supplying encryption keys
     * @return a middleware that encrypts job args
     */
    public static Middleware encryptionMiddleware(EncryptionCodec codec, KeyProvider keys) {
        Objects.requireNonNull(codec, "codec must not be null");
        Objects.requireNonNull(keys, "keys must not be null");

        return (ctx, next) -> {
            Job job = ctx.job();
            String keyId = keys.getCurrentKeyId();
            byte[] key = keys.getKey(keyId);

            // Serialize args to JSON, then encrypt
            String argsJson = Json.encode(job.args());
            byte[] plaintext = argsJson.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = codec.encrypt(plaintext, key);
            String base64Ciphertext = Base64.getEncoder().encodeToString(encrypted);

            List<Object> encryptedArgs = List.of(base64Ciphertext);

            Map<String, Object> newMeta = new LinkedHashMap<>(job.meta());
            newMeta.put(META_ENCODINGS, List.of(ENCODING_ENCRYPTED));
            newMeta.put(META_KEY_ID, keyId);

            Job encryptedJob = copyJobWith(job, encryptedArgs, Collections.unmodifiableMap(newMeta));
            JobContext encryptedCtx = copyContext(ctx, encryptedJob);

            next.handle(encryptedCtx);
        };
    }

    /**
     * Returns a {@link Middleware} that decrypts the job's {@code args} if they
     * were previously encrypted by {@link #encryptionMiddleware}.
     *
     * <p>Checks {@code ojs.codec.encodings} for {@code "binary/encrypted"}.
     * Also supports legacy {@code ojs.encryption.encrypted} flag for backward
     * compatibility. Unencrypted jobs pass through without modification.
     *
     * @param codec the AES-256-GCM codec
     * @param keys  the key provider supplying decryption keys
     * @return a middleware that decrypts job args
     */
    @SuppressWarnings("unchecked")
    public static Middleware decryptionMiddleware(EncryptionCodec codec, KeyProvider keys) {
        Objects.requireNonNull(codec, "codec must not be null");
        Objects.requireNonNull(keys, "keys must not be null");

        return (ctx, next) -> {
            Job job = ctx.job();

            // Detect encryption via new spec keys or legacy keys.
            Object encodings = job.meta().get(META_ENCODINGS);
            boolean isEncrypted =
                    (encodings instanceof List<?> list && list.contains(ENCODING_ENCRYPTED));

            if (!isEncrypted) {
                Object legacyFlag = job.meta().get(LEGACY_META_ENCRYPTED);
                isEncrypted = Boolean.TRUE.equals(legacyFlag)
                        || "true".equals(String.valueOf(legacyFlag));
            }

            if (!isEncrypted) {
                next.handle(ctx);
                return;
            }

            // Read key ID from new key first, then fall back to legacy.
            String keyId = (String) job.meta().get(META_KEY_ID);
            if (keyId == null) {
                keyId = (String) job.meta().get(LEGACY_META_KEY_ID);
            }
            if (keyId == null) {
                throw new IllegalStateException(
                        "Encrypted job missing " + META_KEY_ID + " in meta");
            }
            byte[] key = keys.getKey(keyId);

            // Base64 decode, decrypt, parse JSON back to args
            String base64Ciphertext = (String) job.args().getFirst();
            byte[] encrypted = Base64.getDecoder().decode(base64Ciphertext);
            byte[] plaintext = codec.decrypt(encrypted, key);
            String argsJson = new String(plaintext, StandardCharsets.UTF_8);
            List<Object> originalArgs = (List<Object>) Json.decode(argsJson);

            Job decryptedJob = copyJobWith(job, originalArgs, job.meta());
            JobContext decryptedCtx = copyContext(ctx, decryptedJob);

            next.handle(decryptedCtx);
        };
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Creates a copy of the given job with replaced args and meta. */
    private static Job copyJobWith(Job job, List<Object> args, Map<String, Object> meta) {
        return new Job(
                job.specversion(), job.id(), job.type(), job.queue(),
                args, meta,
                job.priority(), job.timeout(),
                job.scheduledAt(), job.expiresAt(),
                job.retry(), job.unique(), job.schema(),
                job.state(), job.attempt(),
                job.createdAt(), job.enqueuedAt(), job.startedAt(), job.completedAt(),
                job.error(), job.result(), job.tags());
    }

    /** Creates a new JobContext wrapping a different job but preserving other fields. */
    private static JobContext copyContext(JobContext ctx, Job job) {
        return new JobContext(
                ctx.serverUrl(), job, ctx.workflowId(),
                ctx.parentResults(), jobId -> ctx.heartbeat());
    }
}
