package com.ibrasoft.lensbridge.service.board.offline;

import com.ibrasoft.lensbridge.dto.board.response.SigningKeyView;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.service.agent.Ed25519TestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Key loading, key ids and package signatures, checked against the JDK's own verifier and
 * against a fixed cross-language test vector shared with the Go agent and the Node packer.
 */
class ContentSigningServiceTest {

    /**
     * Cross-language test vector. The Go agent ({@code internal/trust}) and the Node packer
     * must derive the same values from the same seed:
     * <pre>
     * seed (hex)      0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20
     * public key (b64) ebVWLo/mVPlAeLES6KmLp5AfhTrmlb7X4OORC60ElmQ=
     * keyId           65b60673d6ed884b
     * </pre>
     * Signing the manifest {@code {"format":"mbu"}} (see {@link #crossLanguageTestVector()})
     * signs these 37 bytes, hex:
     * <pre>
     * 6d7573616c6c6168626f6172642d6d62752d76320a7b22666f726d6174223a226d6275227d
     * </pre>
     * and, Ed25519 being deterministic, always yields this signature (base64):
     * <pre>
     * PDYOh2jMeZK5PgkxhjAIOXJzTLyWVsGfilFMSDukwg0iTN9xmy3KgQjJwjTM1NnucaX01mVcFoPxDhBebodbAw==
     * </pre>
     */
    static final byte[] VECTOR_SEED = HexFormat.of().parseHex(
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
    static final String VECTOR_KEY_ID = "65b60673d6ed884b";

    private static final String VECTOR_SEED_B64 = Base64.getEncoder().encodeToString(VECTOR_SEED);

    /** Verifies with the JDK provider directly, independent of any code under test. */
    static boolean jdkVerify(byte[] rawPublicKey, byte[] message, byte[] signature) throws Exception {
        byte[] x509 = new byte[44];
        System.arraycopy(HexFormat.of().parseHex("302a300506032b6570032100"), 0, x509, 0, 12);
        System.arraycopy(rawPublicKey, 0, x509, 12, 32);
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(signature);
    }

    @Test
    void crossLanguageTestVector() throws Exception {
        ContentSigningService service = new ContentSigningService(VECTOR_SEED_B64, "");
        SigningKeyView key = service.publicKeys().get(0);
        byte[] manifest = "{\"format\":\"mbu\"}".getBytes(StandardCharsets.UTF_8);
        byte[] message = ContentSigningService.mbuSignedMessage(manifest);
        byte[] signature = service.signManifest(manifest);

        // Printed so the values can be copied into the other implementations' tests.
        System.out.println("content key test vector: seed=" + HexFormat.of().formatHex(VECTOR_SEED)
                + " publicKey=" + key.getPublicKey()
                + " keyId=" + key.getKeyId()
                + " signedMessageHex=" + HexFormat.of().formatHex(message)
                + " signature=" + Base64.getEncoder().encodeToString(signature));

        assertThat(key.getKeyId()).isEqualTo(VECTOR_KEY_ID);
        assertThat(service.currentKeyId()).isEqualTo(VECTOR_KEY_ID);
        assertThat(key.getPublicKey()).isEqualTo("ebVWLo/mVPlAeLES6KmLp5AfhTrmlb7X4OORC60ElmQ=");
        assertThat(HexFormat.of().formatHex(message))
                .isEqualTo("6d7573616c6c6168626f6172642d6d62752d76320a7b22666f726d6174223a226d6275227d");
        assertThat(Base64.getEncoder().encodeToString(signature)).isEqualTo("PDYOh2jMeZK5PgkxhjAIOXJzTLyWVsGfilFMSDukwg0iTN9xmy3KgQjJwjTM1NnucaX01mVcFoPxDhBebodbAw==");
        assertThat(jdkVerify(Base64.getDecoder().decode(key.getPublicKey()), message, signature)).isTrue();
    }

    /** The derived public key must be the one the JDK generated for that seed, and the key id its hash prefix. */
    @Test
    void publicKeyAndKeyIdAreDerivedFromTheSeed() throws Exception {
        KeyPair pair = Ed25519TestUtil.generate();
        byte[] seed = ((EdECPrivateKey) pair.getPrivate()).getBytes().orElseThrow();
        byte[] expectedPublic = Ed25519TestUtil.rawPublicKey(pair.getPublic());

        ContentSigningService service = new ContentSigningService(Base64.getEncoder().encodeToString(seed), "");

        SigningKeyView key = service.publicKeys().get(0);
        assertThat(Base64.getDecoder().decode(key.getPublicKey())).isEqualTo(expectedPublic);
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(expectedPublic);
        assertThat(key.getKeyId())
                .isEqualTo(HexFormat.of().formatHex(digest, 0, 8))
                .matches("^[0-9a-f]{16}$");
    }

    @Test
    void signatureCoversThePrefixFollowedByTheExactManifestBytes() throws Exception {
        ContentSigningService service = new ContentSigningService(VECTOR_SEED_B64, "");
        byte[] manifest = "{\"format\":\"mbu\",\"formatVersion\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] publicKey = Base64.getDecoder().decode(service.publicKeys().get(0).getPublicKey());

        byte[] signature = service.signManifest(manifest);

        byte[] prefixed = ("musallahboard-mbu-v2\n" + new String(manifest, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        assertThat(jdkVerify(publicKey, prefixed, signature)).isTrue();
        // Not the bare manifest, and not another domain's prefix.
        assertThat(jdkVerify(publicKey, manifest, signature)).isFalse();
        byte[] otherDomain = ("musallahboard-auth-v1\n" + new String(manifest, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        assertThat(jdkVerify(publicKey, otherDomain, signature)).isFalse();
    }

    @Test
    void previousKeysArePublishedAfterTheCurrentOneWithoutDuplicates() {
        byte[] previous = Ed25519TestUtil.rawPublicKey(Ed25519TestUtil.generate().getPublic());
        String previousB64 = Base64.getEncoder().encodeToString(previous);
        String currentB64 = new ContentSigningService(VECTOR_SEED_B64, "").publicKeys().get(0).getPublicKey();

        ContentSigningService service = new ContentSigningService(VECTOR_SEED_B64,
                " " + previousB64 + " ,," + currentB64 + "," + previousB64);

        assertThat(service.publicKeys()).extracting(SigningKeyView::getPublicKey)
                .containsExactly(currentB64, previousB64);
        assertThat(service.publicKeys().get(1).getKeyId()).isEqualTo(ContentSigningService.keyId(previous));
    }

    @Test
    void withoutAKeyTheServiceStartsPublishesNothingAndSigningIsA503() {
        for (String unset : new String[]{null, "", "   "}) {
            ContentSigningService service = new ContentSigningService(unset, "");

            assertThat(service.isConfigured()).isFalse();
            assertThat(service.publicKeys()).isEmpty();
            assertThat(service.currentKeyId()).isNull();
            assertThatThrownBy(service::requireConfigured)
                    .isInstanceOfSatisfying(ApiResponseException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            assertThatThrownBy(() -> service.signManifest(new byte[]{1}))
                    .isInstanceOfSatisfying(ApiResponseException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    /** Without a current key there is nothing to rotate from, so previous keys are not published either. */
    @Test
    void previousKeysAloneAreNotPublished() {
        String previous = Base64.getEncoder().encodeToString(
                Ed25519TestUtil.rawPublicKey(Ed25519TestUtil.generate().getPublic()));

        assertThat(new ContentSigningService("", previous).publicKeys()).isEmpty();
    }

    @Test
    void aMalformedKeyFailsStartupRatherThanSilentlyDisablingSigning() {
        assertThatThrownBy(() -> new ContentSigningService("not base64!", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not valid base64");
        assertThatThrownBy(() -> new ContentSigningService(Base64.getEncoder().encodeToString(new byte[31]), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32-byte");
        assertThatThrownBy(() -> new ContentSigningService(VECTOR_SEED_B64, "AAAA"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("previous-public-keys");
        assertThatThrownBy(() -> new ContentSigningService(VECTOR_SEED_B64, "%%%"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("previous-public-keys");
    }
}
