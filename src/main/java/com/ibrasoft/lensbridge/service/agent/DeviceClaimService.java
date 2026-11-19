package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * The online claim (contract C2): a board set up with an online setup file proves it holds the
 * file's claim secret and binds the device key it generated to its pre-created device.
 * <pre>
 *   proof = base64url( HMAC-SHA256( key = claim secret bytes, msg = deviceId + "\n" + publicKey ) )
 * </pre>
 * {@code deviceId} is the lowercase UUID, {@code publicKey} the base64 string exactly as sent.
 * <ul>
 *   <li>Unknown, revoked, never given an online setup file, or a wrong proof: refused, all the
 *       same way, so the answer says nothing about which devices exist.</li>
 *   <li>No key yet: this one is bound.</li>
 *   <li>This same key already: success again, so a board whose response was lost can retry.</li>
 *   <li>A different key: conflict. Only a new SD card does that, and it needs a new
 *       enrollment token (see {@link DeviceEnrollmentService}).</li>
 * </ul>
 * The secret is kept after the bind, for those retries, until a new online setup file
 * replaces it. It and the proof are never logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceClaimService {

    private static final int ED25519_PUBLIC_KEY_LEN = 32;
    private static final String HMAC = "HmacSHA256";

    private final DeviceRepository deviceRepository;

    public sealed interface Outcome {
        record Ok(Device device) implements Outcome {}
        record BadRequest(String reason) implements Outcome {}
        /** Any reason the claim cannot be accepted, deliberately not told apart. */
        record Refused() implements Outcome {}
        record KeyConflict() implements Outcome {}
    }

    @Transactional
    public Outcome claim(UUID deviceId, String publicKeyBase64, String proofBase64Url,
                         String agentVersion, String hostname, String remoteIp) {
        byte[] publicKey;
        try {
            publicKey = Base64.getDecoder().decode(publicKeyBase64);
        } catch (IllegalArgumentException e) {
            return new Outcome.BadRequest("publicKey is not base64");
        }
        if (publicKey.length != ED25519_PUBLIC_KEY_LEN) {
            return new Outcome.BadRequest("publicKey must be 32 bytes, got " + publicKey.length);
        }
        byte[] proof;
        try {
            proof = Base64.getUrlDecoder().decode(proofBase64Url);
        } catch (IllegalArgumentException e) {
            return new Outcome.BadRequest("proof is not base64url");
        }

        Optional<Device> found = deviceRepository.findById(deviceId);
        if (found.isEmpty() || found.get().getRevokedAt() != null || found.get().getClaimSecret() == null) {
            log.warn("Claim refused for device {}: {}", deviceId, found.isEmpty() ? "unknown device"
                    : found.get().getRevokedAt() != null ? "revoked" : "no online setup file issued");
            return new Outcome.Refused();
        }
        Device device = found.get();
        byte[] expected = proof(device.getClaimSecret(), deviceId, publicKeyBase64);
        if (!MessageDigest.isEqual(expected, proof)) {
            log.warn("Claim refused for device {}: wrong proof (an older setup file, or not this board's)", deviceId);
            return new Outcome.Refused();
        }

        if (device.getPublicKey() == null && deviceRepository.bindPublicKeyIfUnset(deviceId, publicKey) == 1) {
            log.info("Device {} claimed: key bound (hostname {}, agent {})", deviceId, hostname, agentVersion);
        }
        // Reloaded: the bind cleared the persistence context, and another claim may have won.
        device = deviceRepository.findById(deviceId).orElseThrow();
        if (!Arrays.equals(device.getPublicKey(), publicKey)) {
            log.warn("Claim refused for device {}: it is bound to a different key", deviceId);
            return new Outcome.KeyConflict();
        }

        if (device.getEnrolledAt() == null) device.setEnrolledAt(Instant.now());
        device.setAgentVersion(agentVersion);
        device.setLastSeenIp(remoteIp);
        return new Outcome.Ok(deviceRepository.save(device));
    }

    /** The proof a board holding {@code claimSecret} sends for this device and key. */
    public static byte[] proof(byte[] claimSecret, UUID deviceId, String publicKeyBase64) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(claimSecret, HMAC));
            return mac.doFinal((deviceId + "\n" + publicKeyBase64).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is mandatory in every JRE", e);
        }
    }
}
