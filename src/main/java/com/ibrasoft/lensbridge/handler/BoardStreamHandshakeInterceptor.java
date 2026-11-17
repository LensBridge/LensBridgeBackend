package com.ibrasoft.lensbridge.handler;

import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.service.agent.http.DeviceRequestAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates the upgrade request of {@code /api/refresh-musallahboard} with the device's
 * enrolled key, the same {@code X-MB-*} signature the agent puts on its HTTP requests
 * ({@link DeviceRequestAuthenticator}, agent {@code docs/architecture.md} section 9.2): a
 * {@code GET} of the path with an empty body.
 * <p>
 * The rules, during the staged rollout:
 * <ul>
 *   <li>A signature that is present (any of the three headers) is always verified, and a bad
 *       one is always refused with 401. A {@code ?deviceId=} that names a different device is
 *       refused too: the query is not covered by the signature, so the header is what counts.</li>
 *   <li>Each accepted signed upgrade must carry a newer timestamp than the last one accepted for
 *       that device. The scheme itself accepts replays inside its five-minute window, which is
 *       harmless for idempotent reads but not here, where a replayed upgrade would take the
 *       board's place on its channel. The agent signs every dial afresh.</li>
 *   <li>An unsigned upgrade (an agent that predates signing) is refused when
 *       {@code musallahboard.board-stream.require-signature} is true. While it is false it is
 *       accepted and logged, and {@link BoardStreamHandler} never lets it displace a signed
 *       connection for the same device.</li>
 * </ul>
 * Turn the property on once the logs stop showing unsigned connections: then the whole fleet
 * runs an agent that signs.
 */
@Component
@Slf4j
public class BoardStreamHandshakeInterceptor implements HandshakeInterceptor {

    /** Handshake attribute: the device the signature proved, when the upgrade was signed. */
    static final String SIGNED_DEVICE_ATTR = "board.signedDeviceId";

    private static final byte[] NO_BODY = new byte[0];

    private final DeviceRequestAuthenticator authenticator;
    private final boolean requireSignature;

    /** Newest accepted timestamp per device, so a captured upgrade cannot be replayed. */
    private final Map<UUID, Long> lastTimestamp = new ConcurrentHashMap<>();

    public BoardStreamHandshakeInterceptor(
            DeviceRequestAuthenticator authenticator,
            @Value("${musallahboard.board-stream.require-signature:false}") boolean requireSignature) {
        this.authenticator = authenticator;
        this.requireSignature = requireSignature;
        if (!requireSignature) {
            log.info("Board stream accepts unsigned upgrades from older agents "
                    + "(musallahboard.board-stream.require-signature=false)");
        }
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        HttpServletRequest http = servletRequest.getServletRequest();
        UUID named = BoardStreamHandler.parseDeviceId(request.getURI());

        if (!hasSignatureHeader(http)) {
            if (requireSignature) {
                log.warn("Board stream refused an unsigned upgrade for device {} ({})", named, userAgent(http));
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            // Logged every time on purpose: when these stop, the fleet has migrated and
            // require-signature can be turned on.
            log.warn("Board stream accepted an unsigned upgrade for device {} ({}); "
                    + "the board needs an agent that signs it before "
                    + "musallahboard.board-stream.require-signature is turned on", named, userAgent(http));
            return true;
        }

        Device device;
        try {
            device = authenticator.authenticate(http, NO_BODY);
        } catch (ApiResponseException e) {
            // The authenticator has already logged which check failed.
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        UUID deviceId = device.getId();
        if (named != null && !named.equals(deviceId)) {
            log.warn("Board stream refused a signed upgrade for device {} that names device {} in its query",
                    deviceId, named);
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        // Already parsed and range-checked by the authenticator.
        long timestamp = Long.parseLong(http.getHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER).trim());
        if (!advance(deviceId, timestamp)) {
            log.warn("Board stream refused a replayed upgrade for device {} (timestamp {} not newer than the last)",
                    deviceId, timestamp);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(SIGNED_DEVICE_ATTR, deviceId);
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do: the handler registers the session once it is established.
    }

    /** True, and remembered, when {@code timestamp} is newer than any accepted for the device. */
    private boolean advance(UUID deviceId, long timestamp) {
        boolean[] fresh = {false};
        lastTimestamp.compute(deviceId, (id, previous) -> {
            if (previous == null || timestamp > previous) {
                fresh[0] = true;
                return timestamp;
            }
            return previous;
        });
        return fresh[0];
    }

    private static boolean hasSignatureHeader(HttpServletRequest request) {
        return request.getHeader(DeviceRequestAuthenticator.DEVICE_ID_HEADER) != null
                || request.getHeader(DeviceRequestAuthenticator.TIMESTAMP_HEADER) != null
                || request.getHeader(DeviceRequestAuthenticator.SIGNATURE_HEADER) != null;
    }

    private static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "no User-Agent";
        // Logged verbatim otherwise: keep a hostile value to one short line.
        String cleaned = ua.replaceAll("[\\p{Cntrl}]", "?");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) + "..." : cleaned;
    }
}
