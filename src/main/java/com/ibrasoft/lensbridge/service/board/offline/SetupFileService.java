package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Builds a board's setup file: a signed {@code provision} package ({@code .mbu}, contract C1)
 * holding one file, {@code provision.json}, that gives a fresh board its identity:
 * <pre>
 * {"format":1, "deviceId":"…", "displayName":"North Musallah", "sequence":…, "issuedAt":"…",
 *  "backendUrl":"https://…", "claimSecret":"&lt;base64url, no padding, 32 bytes&gt;"}
 * </pre>
 * {@code backendUrl} and {@code claimSecret} are present only in an online setup file. An
 * offline board never talks to this server, so its file carries neither.
 * <p>
 * It is signed with the content key by the same {@link MbuPackager} as content packages; the
 * board verifies it against the same pinned content keys. Like content, it can only say who
 * the board is and where its backend is: never keys, software or anything that runs.
 * <p>
 * Every file gets a sequence greater than the last one issued for the device (a board only
 * takes a newer one), and every online file a freshly generated claim secret, which replaces
 * the stored one: a file downloaded earlier can no longer claim the board.
 */
@Service
@Slf4j
public class SetupFileService {

    /** {@code provision.json} format this backend writes. */
    public static final int PROVISION_FORMAT = 1;
    public static final String PROVISION_ENTRY = "provision.json";
    static final int CLAIM_SECRET_BYTES = 32;

    private final DeviceRepository deviceRepository;
    private final ContentSigningService signingService;
    private final MbuPackager packager;
    private final Clock clock;
    private final SecureRandom random;
    /** Where an online board reaches this server; null when it cannot be worked out. */
    private final String backendUrl;

    @Autowired
    public SetupFileService(DeviceRepository deviceRepository,
                            ObjectMapper objectMapper,
                            ContentSigningService signingService,
                            @Value("${musallahboard.agent.backendUrl:}") String backendUrl,
                            @Value("${musallahboard.agent.websocketUrl:}") String websocketUrl) {
        this(deviceRepository, objectMapper, signingService, resolveBackendUrl(backendUrl, websocketUrl),
                Clock.systemUTC(), new SecureRandom());
    }

    SetupFileService(DeviceRepository deviceRepository, ObjectMapper objectMapper,
                     ContentSigningService signingService, String backendUrl, Clock clock, SecureRandom random) {
        this.deviceRepository = deviceRepository;
        this.signingService = signingService;
        this.packager = new MbuPackager(objectMapper, signingService);
        this.backendUrl = backendUrl;
        this.clock = clock;
        this.random = random;
    }

    /** {@code provision.json}. Optional fields are left out, not written as null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"format", "deviceId", "displayName", "sequence", "issuedAt", "backendUrl", "claimSecret"})
    record ProvisionFile(int format, String deviceId, String displayName, long sequence, String issuedAt,
                         String backendUrl, String claimSecret) {}

    /**
     * Issues the file: allocates the sequence and, online, rotates the secret, then signs.
     * Called inside a larger transaction (a USB kit), it joins it, and nothing it stores is
     * kept unless that transaction commits.
     *
     * @param online include {@code backendUrl} and a newly rotated {@code claimSecret}
     * @throws ApiResponseException 404 unknown device, 409 revoked, 503 no content signing key,
     *                              or (online only) no backend URL configured
     */
    @Transactional
    public OfflineBundle build(UUID deviceId, boolean online) {
        signingService.requireConfigured();
        if (online && backendUrl == null) {
            throw new ApiResponseException(HttpStatus.SERVICE_UNAVAILABLE, ErrorResponse.of(
                    "No backend URL for online boards is configured on this server "
                            + "(set musallahboard.agent.backendUrl, or musallahboard.agent.websocketUrl)"));
        }

        // The atomic update comes first (see advanceProvisionSequence): two downloads at once
        // are serialized on the device's row, so each gets its own sequence and the secret
        // left stored is that of the newest file.
        Instant now = clock.instant();
        if (deviceRepository.advanceProvisionSequence(deviceId, now.toEpochMilli()) == 0) {
            boolean exists = deviceRepository.existsById(deviceId);
            throw exists
                    ? new ApiResponseException(HttpStatus.CONFLICT, ErrorResponse.of("Device is revoked"))
                    : new ApiResponseException(HttpStatus.NOT_FOUND, ErrorResponse.of("Device not found"));
        }
        String claimSecret = null;
        if (online) {
            byte[] secret = new byte[CLAIM_SECRET_BYTES];
            random.nextBytes(secret);
            deviceRepository.replaceClaimSecret(deviceId, secret);
            claimSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        }
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalStateException("Device " + deviceId + " vanished while locked"));
        long sequence = device.getProvisionSequence();

        String issuedAt = now.truncatedTo(ChronoUnit.SECONDS).toString();
        byte[] provision = packager.toJson(new ProvisionFile(PROVISION_FORMAT, deviceId.toString(),
                device.getDisplayName(), sequence, issuedAt, online ? backendUrl : null, claimSecret));
        MbuManifest manifest = new MbuManifest("mbu", OfflineBundleService.FORMAT_VERSION, "provision",
                issuedAt, sequence, deviceId.toString(), null,
                List.of(MbuPackager.fileEntry(PROVISION_ENTRY, provision)), null);

        // Never the secret itself.
        log.info("Built setup file {} for device {} ({})", sequence, deviceId,
                online ? "online, claim secret rotated" : "offline");
        return packager.pack("musallahboard-setup-" + deviceId.toString().substring(0, 8) + ".mbu", manifest,
                List.of(new OfflineBundle.Entry(PROVISION_ENTRY, provision, false)));
    }

    /**
     * The configured backend URL, else the origin of the agent WebSocket URL
     * ({@code wss://api.example.org/api/agent/ws} gives {@code https://api.example.org}).
     * Null when neither gives an http(s) origin.
     */
    static String resolveBackendUrl(String configured, String websocketUrl) {
        if (configured != null && !configured.isBlank()) {
            String url = configured.trim();
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        if (websocketUrl == null || websocketUrl.isBlank()) return null;
        try {
            URI ws = URI.create(websocketUrl.trim());
            String scheme = switch (ws.getScheme() == null ? "" : ws.getScheme()) {
                case "wss" -> "https";
                case "ws" -> "http";
                default -> null;
            };
            if (scheme == null || ws.getHost() == null) return null;
            return scheme + "://" + ws.getRawAuthority();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
