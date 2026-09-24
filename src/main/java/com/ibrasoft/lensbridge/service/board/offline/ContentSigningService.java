package com.ibrasoft.lensbridge.service.board.offline;

import com.ibrasoft.lensbridge.dto.board.response.SigningKeyView;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the backend's <em>content</em> signing key and signs {@code .mbu} manifests with it.
 * See MusallahBoard {@code agent/docs/architecture.md}, sections 4.2, 5 and 9.3.
 * <p>
 * The content key is one of two trust roots on a board. It can only vouch for content
 * (payloads and posters for one device); software is signed by a separate release key this
 * backend never sees. So a leak of this key puts the wrong posters on screens, nothing worse,
 * and it is rotated by moving the old public key into
 * {@code musallahboard.content-signing.previous-public-keys} for as long as boards may still
 * hold packages signed with it.
 * <p>
 * Key formats are the ones every tool in the system shares ({@code mbpack keygen}, the Node
 * packer, the Go agent): the private key is configured as the base64 of the 32-byte Ed25519
 * seed, and public keys travel as the base64 of the raw 32-byte point. The key id is the
 * lowercase hex of the first 8 bytes of SHA-256 over that raw public key.
 * <p>
 * The key is optional. A deployment without one still starts and serves everything else:
 * the signed endpoints answer 503 via {@link #requireConfigured()}, enrollment hands out an
 * empty key list, and a single WARN at startup says what is missing. A key that is
 * configured but malformed is a startup failure instead, because silently running unsigned
 * would look exactly like "not configured" and hide the typo.
 */
@Service
@Slf4j
public class ContentSigningService {

    /** Domain separation prefix for package signatures; see section 4.2. */
    public static final String MBU_SIGNATURE_PREFIX = "musallahboard-mbu-v2\n";

    private static final int SEED_LEN = 32;
    private static final int RAW_PUBLIC_KEY_LEN = 32;

    /**
     * DER prefix of a PKCS#8 PrivateKeyInfo for Ed25519 (RFC 8410): version 0, the Ed25519
     * AlgorithmIdentifier, and an OCTET STRING wrapping the 32-byte seed as another OCTET
     * STRING. Appending the seed gives a complete encoding the JDK's KeyFactory accepts.
     */
    private static final byte[] PKCS8_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");

    /** DER prefix of an Ed25519 SubjectPublicKeyInfo; the raw key is the 32 bytes after it. */
    private static final byte[] X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    /** The configured signing key, or null when signing is not configured. */
    private final SigningKey current;

    /** Every public content key to publish, current first. Empty when not configured. */
    private final List<SigningKeyView> publishedKeys;

    private record SigningKey(PrivateKey privateKey, byte[] rawPublicKey, String keyId) {}

    /**
     * @param privateKeyBase64      base64 of the 32-byte seed; empty or blank disables signing.
     *                              Falls back to the {@code MUSALLAHBOARD_CONTENT_SIGNING_KEY}
     *                              environment variable so a deployment that only sets the
     *                              variable needs no properties change.
     * @param previousPublicKeysCsv comma-separated base64 raw public keys of retired content
     *                              keys, still published so boards keep trusting packages
     *                              they already hold during a rotation
     * @throws IllegalStateException if either value is present but malformed
     */
    public ContentSigningService(
            @Value("${musallahboard.content-signing.private-key:${MUSALLAHBOARD_CONTENT_SIGNING_KEY:}}")
            String privateKeyBase64,
            @Value("${musallahboard.content-signing.previous-public-keys:}")
            String previousPublicKeysCsv) {
        this.current = isBlank(privateKeyBase64) ? null : loadSigningKey(privateKeyBase64.trim());

        if (current == null) {
            this.publishedKeys = List.of();
            log.warn("Content signing is not configured (musallahboard.content-signing.private-key / "
                    + "MUSALLAHBOARD_CONTENT_SIGNING_KEY). Board content packages cannot be built: "
                    + "POST /api/agent/content-bundle and the admin offline-bundle download will answer 503, "
                    + "and newly enrolled boards receive no content key.");
            if (!isBlank(previousPublicKeysCsv)) {
                log.warn("musallahboard.content-signing.previous-public-keys is set but ignored "
                        + "because no current content signing key is configured.");
            }
            return;
        }

        // Keyed by id so a previous key that repeats the current one (or another) is listed once.
        Map<String, SigningKeyView> keys = new LinkedHashMap<>();
        keys.put(current.keyId(), view(current.rawPublicKey()));
        for (byte[] previous : parsePreviousKeys(previousPublicKeysCsv)) {
            SigningKeyView v = view(previous);
            keys.putIfAbsent(v.getKeyId(), v);
        }
        this.publishedKeys = List.copyOf(keys.values());
        log.info("Content signing key {} loaded; publishing {} content key(s)", current.keyId(), publishedKeys.size());
    }

    /** True when a signing key is configured. */
    public boolean isConfigured() {
        return current != null;
    }

    /**
     * Throws the 503 every signed endpoint returns when no key is configured. Call it before
     * doing any expensive work, so a misconfigured server fails fast and says why.
     */
    public void requireConfigured() {
        if (current == null) {
            throw new ApiResponseException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorResponse.of("Content signing is not configured on this server "
                            + "(set musallahboard.content-signing.private-key / MUSALLAHBOARD_CONTENT_SIGNING_KEY)"));
        }
    }

    /** Key id of the current signing key; null when not configured. */
    public String currentKeyId() {
        return current == null ? null : current.keyId();
    }

    /**
     * The public content keys boards should trust, current first, then any previous keys.
     * Empty when signing is not configured. Returned by enrollment and by
     * {@code GET /api/agent/signing-keys}.
     */
    public List<SigningKeyView> publicKeys() {
        return publishedKeys;
    }

    /**
     * Signs {@code manifestBytes} as an {@code mbu.json}: the signature covers the ASCII
     * prefix {@value #MBU_SIGNATURE_PREFIX} (with its newline) followed by exactly these
     * bytes. The caller must store these same bytes in the zip; re-serializing the manifest
     * afterwards would produce bytes the signature does not cover.
     *
     * @return the 64-byte Ed25519 signature
     * @throws ApiResponseException 503 when signing is not configured
     */
    public byte[] signManifest(byte[] manifestBytes) {
        requireConfigured();
        return sign(current.privateKey(), mbuSignedMessage(manifestBytes));
    }

    /** The exact message a package signature covers: the domain prefix, then the manifest bytes. */
    public static byte[] mbuSignedMessage(byte[] manifestBytes) {
        byte[] prefix = MBU_SIGNATURE_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] message = new byte[prefix.length + manifestBytes.length];
        System.arraycopy(prefix, 0, message, 0, prefix.length);
        System.arraycopy(manifestBytes, 0, message, prefix.length, manifestBytes.length);
        return message;
    }

    /** Lowercase hex of the first 8 bytes of SHA-256 over a raw 32-byte public key. */
    public static String keyId(byte[] rawPublicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawPublicKey);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    /**
     * Derives the raw 32-byte public key for a 32-byte seed.
     * <p>
     * The JDK has no public API for "public key of this private key". Its Ed25519 key pair
     * generator, though, takes the seed as the first 32 bytes it reads from the supplied
     * {@link SecureRandom}, so feeding it the seed reproduces the pair. The constructor then
     * proves the pair matches by signing and verifying a probe message, so a JDK that ever
     * drew its randomness differently would fail loudly at startup rather than publish a
     * public key that does not match the signatures.
     */
    static byte[] rawPublicKeyForSeed(byte[] seed) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        generator.initialize(NamedParameterSpec.ED25519, new FixedSeedRandom(seed));
        KeyPair pair = generator.generateKeyPair();
        return rawPublicKey(pair.getPublic());
    }

    /** The raw 32-byte key out of an Ed25519 public key's X.509 encoding. */
    static byte[] rawPublicKey(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        if (encoded == null || encoded.length != X509_PREFIX.length + RAW_PUBLIC_KEY_LEN
                || !Arrays.equals(encoded, 0, X509_PREFIX.length, X509_PREFIX, 0, X509_PREFIX.length)) {
            throw new IllegalStateException("Unexpected Ed25519 public key encoding");
        }
        return Arrays.copyOfRange(encoded, X509_PREFIX.length, encoded.length);
    }

    /** Wraps a 32-byte seed in the PKCS#8 envelope and decodes it with the JDK provider. */
    static PrivateKey privateKeyForSeed(byte[] seed) throws GeneralSecurityException {
        byte[] pkcs8 = new byte[PKCS8_PREFIX.length + seed.length];
        System.arraycopy(PKCS8_PREFIX, 0, pkcs8, 0, PKCS8_PREFIX.length);
        System.arraycopy(seed, 0, pkcs8, PKCS8_PREFIX.length, seed.length);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    // ==================== Loading ====================

    private static SigningKey loadSigningKey(String base64) {
        byte[] seed;
        try {
            seed = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "musallahboard.content-signing.private-key is not valid base64", e);
        }
        if (seed.length != SEED_LEN) {
            throw new IllegalStateException("musallahboard.content-signing.private-key must be the base64 of a "
                    + SEED_LEN + "-byte Ed25519 seed, got " + seed.length + " bytes");
        }
        try {
            PrivateKey privateKey = privateKeyForSeed(seed);
            byte[] rawPublicKey = rawPublicKeyForSeed(seed);
            // Prove the derived public key belongs to this private key before publishing it.
            byte[] probe = "musallahboard content key self-test".getBytes(StandardCharsets.US_ASCII);
            if (!verify(rawPublicKey, probe, sign(privateKey, probe))) {
                throw new IllegalStateException("Derived content public key does not match the private key");
            }
            return new SigningKey(privateKey, rawPublicKey, keyId(rawPublicKey));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not load the content signing key: " + e.getMessage(), e);
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    private static List<byte[]> parsePreviousKeys(String csv) {
        List<byte[]> keys = new ArrayList<>();
        if (isBlank(csv)) return keys;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(trimmed);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "musallahboard.content-signing.previous-public-keys holds a value that is not base64: " + trimmed, e);
            }
            if (raw.length != RAW_PUBLIC_KEY_LEN) {
                throw new IllegalStateException("musallahboard.content-signing.previous-public-keys must hold base64 raw "
                        + RAW_PUBLIC_KEY_LEN + "-byte Ed25519 public keys, got " + raw.length + " bytes: " + trimmed);
            }
            keys.add(raw);
        }
        return keys;
    }

    private static SigningKeyView view(byte[] rawPublicKey) {
        return new SigningKeyView(keyId(rawPublicKey), Base64.getEncoder().encodeToString(rawPublicKey));
    }

    // ==================== Primitives ====================

    private static byte[] sign(PrivateKey privateKey, byte[] message) {
        try {
            // Signature objects are not thread-safe; one per call is cheap for Ed25519.
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(message);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    private static boolean verify(byte[] rawPublicKey, byte[] message, byte[] signature)
            throws GeneralSecurityException {
        byte[] x509 = new byte[X509_PREFIX.length + rawPublicKey.length];
        System.arraycopy(X509_PREFIX, 0, x509, 0, X509_PREFIX.length);
        System.arraycopy(rawPublicKey, 0, x509, X509_PREFIX.length, rawPublicKey.length);
        PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signature);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * A {@link SecureRandom} that yields the given seed and then refuses to produce more, so
     * {@link #rawPublicKeyForSeed} fails instead of silently mixing in other bytes if the
     * generator ever asks for more than one seed's worth.
     */
    private static final class FixedSeedRandom extends SecureRandom {
        private final byte[] seed;
        private boolean used;

        FixedSeedRandom(byte[] seed) {
            this.seed = seed.clone();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (used || bytes.length != seed.length) {
                throw new IllegalStateException("Ed25519 key generator requested unexpected randomness");
            }
            System.arraycopy(seed, 0, bytes, 0, seed.length);
            Arrays.fill(seed, (byte) 0);
            used = true;
        }
    }
}
