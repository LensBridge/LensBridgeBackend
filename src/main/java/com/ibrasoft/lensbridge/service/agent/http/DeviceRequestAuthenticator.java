package com.ibrasoft.lensbridge.service.agent.http;

import com.ibrasoft.lensbridge.dto.auth.response.MessageResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Authenticates a plain HTTP request made by a board's agent with its enrolled Ed25519 key.
 * See MusallahBoard {@code agent/docs/architecture.md}, section 9.2.
 * <p>
 * The agent sends three headers: {@value #DEVICE_ID_HEADER}, {@value #TIMESTAMP_HEADER}
 * (Unix milliseconds) and {@value #SIGNATURE_HEADER} (base64 Ed25519 signature). The signed
 * message is UTF-8, newline separated, with no trailing newline:
 *
 * <pre>
 * musallahboard-http-v1
 * &lt;METHOD&gt;
 * &lt;request path, no query string&gt;
 * &lt;deviceId&gt;
 * &lt;timestamp&gt;
 * &lt;lowercase hex SHA-256 of the request body (of the empty string if none)&gt;
 * </pre>
 *
 * The prefix separates these signatures from the WebSocket handshake's
 * ({@code musallahboard-auth-v1}) and from package signatures ({@code musallahboard-mbu-v2}),
 * so a signature made for one can never be replayed as another.
 * <p>
 * Endpoints do not call this directly: they declare an {@link AuthenticatedDevice} parameter,
 * and {@link DeviceAuthInterceptor} calls it with the body {@link DeviceBodyCachingFilter}
 * cached, so the hash covers exactly the bytes that were sent. The paths are
 * {@code permitAll} in {@code WebSecurityConfig}; this is their authentication.
 * <p>
 * Replays inside the five-minute window are accepted. That is by design: the endpoints this
 * guards are idempotent reads, so a replay only fetches the same content again.
 */
@Component
@Slf4j
public class DeviceRequestAuthenticator {

    public static final String DEVICE_ID_HEADER = "X-MB-Device-Id";
    public static final String TIMESTAMP_HEADER = "X-MB-Timestamp";
    public static final String SIGNATURE_HEADER = "X-MB-Signature";

    public static final String VERSION_PREFIX = "musallahboard-http-v1";

    /** How far the device's clock may be from ours, either way. */
    public static final Duration MAX_SKEW = Duration.ofMinutes(5);

    private final DeviceRepository deviceRepository;
    private final Ed25519Verifier verifier;
    private final Clock clock;

    @Autowired
    public DeviceRequestAuthenticator(DeviceRepository deviceRepository, Ed25519Verifier verifier) {
        this(deviceRepository, verifier, Clock.systemUTC());
    }

    DeviceRequestAuthenticator(DeviceRepository deviceRepository, Ed25519Verifier verifier, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.verifier = verifier;
        this.clock = clock;
    }

    /**
     * @param request the incoming request; its method, path ({@link HttpServletRequest#getRequestURI()},
     *                which never includes the query string) and headers are read
     * @param body    the raw body bytes exactly as received; null or empty for no body
     * @return the authenticated, non-revoked device
     * @throws ApiResponseException 401 with a JSON message when any check fails
     */
    public Device authenticate(HttpServletRequest request, byte[] body) {
        String deviceIdHeader = request.getHeader(DEVICE_ID_HEADER);
        String timestampHeader = request.getHeader(TIMESTAMP_HEADER);
        String signatureHeader = request.getHeader(SIGNATURE_HEADER);
        if (isBlank(deviceIdHeader) || isBlank(timestampHeader) || isBlank(signatureHeader)) {
            throw unauthorized("missing " + DEVICE_ID_HEADER + ", " + TIMESTAMP_HEADER
                    + " or " + SIGNATURE_HEADER + " header");
        }

        UUID deviceId;
        try {
            deviceId = UUID.fromString(deviceIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw unauthorized(DEVICE_ID_HEADER + " is not a UUID");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw unauthorized(TIMESTAMP_HEADER + " is not Unix milliseconds");
        }
        // Checked before the database is touched, and worded so a board with a wrong clock
        // can tell what is wrong from its sync error.
        long skew = Math.abs(clock.millis() - timestamp);
        if (skew > MAX_SKEW.toMillis()) {
            throw unauthorized("timestamp is more than " + MAX_SKEW.toMinutes()
                    + " minutes from the server clock (check the board's time)");
        }

        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(signatureHeader.trim());
        } catch (IllegalArgumentException e) {
            throw unauthorized(SIGNATURE_HEADER + " is not base64");
        }

        // Unknown and revoked answer alike: the difference is none of the caller's business.
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || device.getRevokedAt() != null || device.getPublicKey() == null) {
            log.warn("Device HTTP auth rejected for {} on {} {}: unknown or revoked device",
                    deviceId, request.getMethod(), request.getRequestURI());
            throw unauthorized("unknown or revoked device");
        }

        byte[] message = message(request.getMethod(), request.getRequestURI(), deviceId, timestamp, body);
        if (!verifier.verify(device.getPublicKey(), message, signature)) {
            log.warn("Device HTTP auth rejected for {} on {} {}: bad signature",
                    deviceId, request.getMethod(), request.getRequestURI());
            throw unauthorized("bad signature");
        }
        return device;
    }

    /**
     * The exact bytes a device signs. Public so tests (and anything else in this codebase that
     * ever needs to act as a device) build it the same way.
     */
    public static byte[] message(String method, String path, UUID deviceId, long timestamp, byte[] body) {
        String s = VERSION_PREFIX + "\n"
                + method + "\n"
                + path + "\n"
                + deviceId + "\n"
                + timestamp + "\n"
                + sha256Hex(body == null ? new byte[0] : body);
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static ApiResponseException unauthorized(String reason) {
        return new ApiResponseException(HttpStatus.UNAUTHORIZED,
                new MessageResponse("Device authentication failed: " + reason));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
