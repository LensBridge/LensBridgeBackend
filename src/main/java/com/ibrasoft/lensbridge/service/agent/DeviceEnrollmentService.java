package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the agent enrollment flow:
 * <ol>
 *   <li>Validate and decode the device-supplied Ed25519 public key.</li>
 *   <li>Atomically consume the one-time enrollment token.</li>
 *   <li>Persist a Device row carrying the public key and metadata reported by the agent.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceEnrollmentService {

    private static final int ED25519_PUBLIC_KEY_LEN = 32;

    private final EnrollmentTokenService enrollmentTokenService;
    private final DeviceRepository deviceRepository;

    public record EnrollResult(Device device) {}

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

        UUID provisionalDeviceId = UUID.randomUUID();
        Optional<EnrollmentToken> consumed = enrollmentTokenService.consume(plaintextToken, provisionalDeviceId);
        if (consumed.isEmpty()) {
            return new Outcome.InvalidToken();
        }
        EnrollmentToken token = consumed.get();

        Device device = Device.builder()
                .id(provisionalDeviceId)
                .displayName(hostname != null && !hostname.isBlank()
                        ? token.getDisplayName() + " (" + hostname + ")"
                        : token.getDisplayName())
                .audience(token.getAudience())
                .publicKey(publicKey)
                .hardwareModel(hardwareModel)
                .agentVersion(agentVersion)
                .lastSeenIp(remoteIp)
                .enrolledAt(Instant.now())
                .build();

        Device saved = deviceRepository.save(device);
        log.info("Enrolled device {} from token {} (issued by {})",
                saved.getId(), token.getId(), token.getCreatedBy());
        return new Outcome.Ok(saved);
    }
}
