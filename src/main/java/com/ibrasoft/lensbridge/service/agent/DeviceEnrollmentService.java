package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.CalculationMethod;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.model.board.Location;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

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

    /** Width of {@code devices.display_name}; Postgres rejects longer values with a 500. */
    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private final EnrollmentTokenService enrollmentTokenService;
    private final DeviceRepository deviceRepository;
    private final BoardConfigRepository boardConfigRepository;

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

        DeviceConfig defaultConfig = DeviceConfig.builder()
                .device(saved)
                .location(Location.builder()
                        .city("Mississauga")
                        .country("Canada")
                        .latitude(43.589)
                        .longitude(-79.6441)
                        .timezone("America/Toronto")
                        .method(CalculationMethod.ISNA)
                        .build())
                .darkModeAfterIsha(true)
                .enableScrollingMessage(true)
                .scrollingMessages(List.of("Welcome to UTM MSA - Follow us @utmmsa for updates!"))
                .build();
        boardConfigRepository.save(defaultConfig);

        log.info("Enrolled device {} from token {} (issued by {})",
                saved.getId(), token.getId(), token.getCreatedBy());
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
