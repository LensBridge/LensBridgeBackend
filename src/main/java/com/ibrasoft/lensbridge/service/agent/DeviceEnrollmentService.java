package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.BoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the agent enrollment flow (the maintainer's {@code musallahboard-agent enroll
 * --token}; boards set up by a setup file claim instead, see {@link DeviceClaimService}):
 * <ol>
 *   <li>Validate and decode the device-supplied Ed25519 public key.</li>
 *   <li>Atomically consume the one-time enrollment token.</li>
 *   <li>A token issued for a board created in the console binds the key to that device,
 *       replacing any key it had (the admin asked for this board to be re-keyed, for example
 *       after an SD card swap) and closing the old key's live session. Any other token
 *       creates a new device carrying the key.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceEnrollmentService {

    private static final int ED25519_PUBLIC_KEY_LEN = 32;

    /** Width of {@code devices.display_name}; Postgres rejects longer values with a 500. */
    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private final EnrollmentTokenService enrollmentTokenService;
    private final DeviceRepository deviceRepository;
    private final BoardConfigRepository boardConfigRepository;
    private final AgentSessionRegistry sessionRegistry;

    public sealed interface Outcome {
        record Ok(Device device) implements Outcome {}
        record InvalidPublicKey(String reason) implements Outcome {}
        record InvalidToken() implements Outcome {}
    }

    @Transactional
    public Outcome enroll(String plaintextToken,
                          String publicKeyBase64,
                          String hostname,
                          String hardwareModel,
                          String agentVersion,
                          String remoteIp) {
        byte[] publicKey;
        try {
            publicKey = Base64.getDecoder().decode(publicKeyBase64);
        } catch (IllegalArgumentException e) {
            return new Outcome.InvalidPublicKey("not base64");
        }
        if (publicKey.length != ED25519_PUBLIC_KEY_LEN) {
            return new Outcome.InvalidPublicKey("expected 32 bytes, got " + publicKey.length);
        }

        Optional<EnrollmentToken> consumed = enrollmentTokenService.consume(plaintextToken);
        if (consumed.isEmpty()) {
            return new Outcome.InvalidToken();
        }
        EnrollmentToken token = consumed.get();
        if (token.getDeviceId() != null) {
            return bindExisting(token, publicKey, hardwareModel, agentVersion, remoteIp);
        }

        Device device = Device.builder()
                .displayName(displayNameFor(token, hostname))
                .audience(token.getAudience())
                .publicKey(publicKey)
                .hardwareModel(hardwareModel)
                .agentVersion(agentVersion)
                .lastSeenIp(remoteIp)
                .enrolledAt(Instant.now())
                .build();

        Device saved = deviceRepository.save(device);

        // The id only exists now that the device is persisted; same transaction as the consume.
        enrollmentTokenService.recordConsumer(token.getId(), saved.getId());

        DeviceConfig defaultConfig = BoardService.defaultConfig(saved);
        boardConfigRepository.save(defaultConfig);

        log.info("Enrolled device {} from token {} (issued by {})",
                saved.getId(), token.getId(), token.getCreatedBy());
        return new Outcome.Ok(saved);
    }

    private Outcome bindExisting(EnrollmentToken token, byte[] publicKey, String hardwareModel,
                                 String agentVersion, String remoteIp) {
        Optional<Device> found = deviceRepository.findById(token.getDeviceId());
        if (found.isEmpty() || found.get().getRevokedAt() != null) {
            log.warn("Enrollment token {} is for device {}, which is {}", token.getId(), token.getDeviceId(),
                    found.isEmpty() ? "gone" : "revoked");
            return new Outcome.InvalidToken();
        }
        Device device = found.get();
        boolean rekeyed = device.getPublicKey() != null && !Arrays.equals(device.getPublicKey(), publicKey);
        device.setPublicKey(publicKey);
        device.setHardwareModel(hardwareModel);
        device.setAgentVersion(agentVersion);
        device.setLastSeenIp(remoteIp);
        if (device.getEnrolledAt() == null) device.setEnrolledAt(Instant.now());
        Device saved = deviceRepository.save(device);
        enrollmentTokenService.recordConsumer(token.getId(), saved.getId());
        if (rekeyed) {
            // The old key must not keep a live session; it can no longer authenticate.
            UUID id = saved.getId();
            AfterCommit.run(() -> sessionRegistry.closeIfPresent(id, CloseStatus.POLICY_VIOLATION.withReason("rekeyed")));
        }
        log.info("Enrolled device {} from token {} (issued by {}){}", saved.getId(), token.getId(),
                token.getCreatedBy(), rekeyed ? ", replacing its previous key" : "");
        return new Outcome.Ok(saved);
    }

    /** Token name plus the hostname the agent reported, clipped to the column. */
    private static String displayNameFor(EnrollmentToken token, String hostname) {
        String name = hostname != null && !hostname.isBlank()
                ? token.getDisplayName() + " (" + hostname + ")"
                : token.getDisplayName();
        return name.length() <= MAX_DISPLAY_NAME_LENGTH ? name : name.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }
}
