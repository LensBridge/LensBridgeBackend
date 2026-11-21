package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.repository.sql.SqliteDataJpaTest;
import jakarta.persistence.EntityManager;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The online claim (contract C2) against the real schema, so the conditional bind is the
 * database's. Uses the cross-repo HMAC test vector the agent's tests use too.
 */
@SqliteDataJpaTest
@Import(DeviceClaimService.class)
class DeviceClaimServiceTest {

    /**
     * The shared vector: deviceId ...0001, claimSecret 32 x 0x01, publicKey the Ed25519 key of
     * seed 32 x 0x02 (base64 std), msg = deviceId + "\n" + publicKey. Checked independently with
     * Python (hmac, cryptography); padded, the proof ends in "=".
     */
    static final String VECTOR_DEVICE_ID = "00000000-0000-0000-0000-000000000001";
    static final String VECTOR_PUBLIC_KEY = "gTl3Dqh9F19Wo1Rmw0x+zMuNipG07jeiXfYPW4/Js5Q=";
    static final String VECTOR_PROOF = "2_nUd8LSmcq1kdXUvLW5ZQkAr_yDjiTuIh2AzpJH2sA";

    @Autowired
    private DeviceClaimService claims;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EntityManager em;

    private final byte[] secret = filled(0x01);
    private UUID deviceId;

    private static byte[] filled(int value) {
        byte[] b = new byte[32];
        Arrays.fill(b, (byte) value);
        return b;
    }

    private static String publicKeyOfSeed(int value) {
        return Base64.getEncoder().encodeToString(
                new Ed25519PrivateKeyParameters(filled(value), 0).generatePublicKey().getEncoded());
    }

    private String proof(byte[] withSecret, UUID id, String publicKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(DeviceClaimService.proof(withSecret, id, publicKey));
    }

    @BeforeEach
    void preCreatedBoard() {
        deviceId = devices.saveAndFlush(Device.builder()
                .displayName("North Musallah").audience(Audience.BOTH).claimSecret(secret).build()).getId();
        em.clear();
    }

    private DeviceClaimService.Outcome claim(UUID id, String publicKey, String proof) {
        DeviceClaimService.Outcome outcome = claims.claim(id, publicKey, proof, "0.5.0", "board-1", "10.0.0.7");
        em.flush();
        em.clear();
        return outcome;
    }

    @Test
    void theSharedTestVector() {
        String publicKey = publicKeyOfSeed(0x02);
        String proof = proof(filled(0x01), UUID.fromString(VECTOR_DEVICE_ID), publicKey);
        System.out.println("claim HMAC test vector: deviceId=" + VECTOR_DEVICE_ID + " claimSecret=32x0x01 (base64url "
                + Base64.getUrlEncoder().withoutPadding().encodeToString(filled(0x01)) + ") publicKey=" + publicKey
                + " proof=" + proof);
        assertThat(publicKey).isEqualTo(VECTOR_PUBLIC_KEY);
        assertThat(proof).isEqualTo(VECTOR_PROOF);
    }

    @Test
    void aFirstClaimBindsTheKeyAndReturnsTheDevice() {
        String key = publicKeyOfSeed(0x02);

        DeviceClaimService.Outcome outcome = claim(deviceId, key, proof(secret, deviceId, key));

        assertThat(outcome).isInstanceOf(DeviceClaimService.Outcome.Ok.class);
        Device bound = devices.findById(deviceId).orElseThrow();
        assertThat(Base64.getEncoder().encodeToString(bound.getPublicKey())).isEqualTo(key);
        assertThat(bound.getEnrolledAt()).isNotNull();
        assertThat(bound.getAgentVersion()).isEqualTo("0.5.0");
        assertThat(bound.getLastSeenIp()).isEqualTo("10.0.0.7");
        assertThat(bound.getClaimSecret()).as("kept for retries").isEqualTo(secret);
    }

    /** A board whose response was lost retries with the same key. */
    @Test
    void claimingAgainWithTheSameKeySucceeds() {
        String key = publicKeyOfSeed(0x02);
        claim(deviceId, key, proof(secret, deviceId, key));
        Instant first = devices.findById(deviceId).orElseThrow().getEnrolledAt();
        em.clear();

        assertThat(claim(deviceId, key, proof(secret, deviceId, key))).isInstanceOf(DeviceClaimService.Outcome.Ok.class);
        assertThat(devices.findById(deviceId).orElseThrow().getEnrolledAt()).isEqualTo(first);
    }

    @Test
    void anotherKeyIsAConflictAndTheFirstStays() {
        String key = publicKeyOfSeed(0x02);
        claim(deviceId, key, proof(secret, deviceId, key));
        String other = publicKeyOfSeed(0x03);

        assertThat(claim(deviceId, other, proof(secret, deviceId, other)))
                .isInstanceOf(DeviceClaimService.Outcome.KeyConflict.class);
        assertThat(Base64.getEncoder().encodeToString(devices.findById(deviceId).orElseThrow().getPublicKey()))
                .isEqualTo(key);
    }

    @Test
    void aWrongProofIsRefusedAndBindsNothing() {
        String key = publicKeyOfSeed(0x02);

        assertThat(claim(deviceId, key, proof(filled(0x09), deviceId, key)))
                .as("an older setup file's secret").isInstanceOf(DeviceClaimService.Outcome.Refused.class);
        assertThat(claim(deviceId, key, proof(secret, deviceId, publicKeyOfSeed(0x03))))
                .as("a proof for another key").isInstanceOf(DeviceClaimService.Outcome.Refused.class);
        assertThat(devices.findById(deviceId).orElseThrow().getPublicKey()).isNull();
    }

    /** The proof covers the exact publicKey string sent, so re-encoding it breaks the proof. */
    @Test
    void theProofCoversThePublicKeyStringAsSent() {
        String key = publicKeyOfSeed(0x02);
        String proof = proof(secret, deviceId, key);

        assertThat(claim(deviceId, key.replace("=", ""), proof)).isInstanceOf(DeviceClaimService.Outcome.Refused.class);
    }

    @Test
    void paddedAndUnpaddedProofsAreBothAccepted() {
        String key = publicKeyOfSeed(0x02);
        String padded = Base64.getUrlEncoder().encodeToString(DeviceClaimService.proof(secret, deviceId, key));
        assertThat(padded).endsWith("=");

        assertThat(claim(deviceId, key, padded)).isInstanceOf(DeviceClaimService.Outcome.Ok.class);
    }

    @Test
    void unknownRevokedAndNeverOnlineDevicesAreRefusedAlike() {
        String key = publicKeyOfSeed(0x02);
        UUID unknown = UUID.randomUUID();
        assertThat(claim(unknown, key, proof(secret, unknown, key))).isInstanceOf(DeviceClaimService.Outcome.Refused.class);

        UUID offline = devices.saveAndFlush(Device.builder().displayName("Offline").audience(Audience.BOTH).build()).getId();
        assertThat(claim(offline, key, proof(secret, offline, key))).isInstanceOf(DeviceClaimService.Outcome.Refused.class);

        Device revoked = devices.findById(deviceId).orElseThrow();
        revoked.setRevokedAt(Instant.now());
        devices.saveAndFlush(revoked);
        em.clear();
        assertThat(claim(deviceId, key, proof(secret, deviceId, key))).isInstanceOf(DeviceClaimService.Outcome.Refused.class);
        assertThat(devices.findById(deviceId).orElseThrow().getPublicKey()).isNull();
    }

    @Test
    void malformedInputIsABadRequest() {
        String key = publicKeyOfSeed(0x02);
        assertThat(claim(deviceId, "not base64!", proof(secret, deviceId, key)))
                .isInstanceOf(DeviceClaimService.Outcome.BadRequest.class);
        assertThat(claim(deviceId, Base64.getEncoder().encodeToString(new byte[31]), proof(secret, deviceId, key)))
                .isInstanceOf(DeviceClaimService.Outcome.BadRequest.class);
        assertThat(claim(deviceId, key, "+/+/"))
                .isInstanceOf(DeviceClaimService.Outcome.BadRequest.class);
    }

    /** Two boards racing for one device: the database lets exactly one bind. */
    @Test
    void theBindOnlyWinsOnce() {
        byte[] a = Base64.getDecoder().decode(publicKeyOfSeed(0x02));
        byte[] b = Base64.getDecoder().decode(publicKeyOfSeed(0x03));
        assertThat(devices.bindPublicKeyIfUnset(deviceId, a)).isEqualTo(1);
        assertThat(devices.bindPublicKeyIfUnset(deviceId, b)).isEqualTo(0);
        assertThat(devices.findById(deviceId).orElseThrow().getPublicKey()).isEqualTo(a);
    }
}
