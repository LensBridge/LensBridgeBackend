package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push channel for kiosk pages: tells a board its content or config changed so it can
 * refetch without waiting out its ten-minute poll.
 * <p>
 * Every board on this channel is an enrolled device. A connection must name a known,
 * unrevoked {@code ?deviceId=<uuid>} or it is closed on the spot — there is no anonymous
 * or pre-enrollment state here. A board that has not been enrolled has no business on the
 * stream: it has no payload to refetch and nothing can be addressed to it.
 * <p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoardStreamHandler extends TextWebSocketHandler {

    // Should probably be enums but this is sufficient for now
    static final CloseStatus CLOSE_NO_DEVICE = new CloseStatus(4001, "device_id_required");
    static final CloseStatus CLOSE_UNKNOWN_DEVICE = new CloseStatus(4002, "unknown_device");
    static final CloseStatus CLOSE_REVOKED = new CloseStatus(4003, "device_revoked");
    static final CloseStatus CLOSE_SUPERSEDED = CloseStatus.NORMAL.withReason("superseded");

    /** Session attribute holding the device a connection was accepted as. */
    private static final String DEVICE_ATTR = "board.deviceId";

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;

    /**
     * Live boards keyed by device. One session per device: a reloading kiosk can briefly
     * open its replacement before the old socket finishes closing, so the newcomer wins
     * and the stale one is closed rather than left to accumulate.
     */
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        UUID deviceId = parseDeviceId(session);
        if (deviceId == null) {
            close(session, CLOSE_NO_DEVICE);
            return;
        }

        Optional<Device> device = deviceRepository.findById(deviceId);
        if (device.isEmpty()) {
            log.warn("Board stream session={} named unknown device {}", session.getId(), deviceId);
            close(session, CLOSE_UNKNOWN_DEVICE);
            return;
        }
        if (device.get().getRevokedAt() != null) {
            log.warn("Board stream session={} is a revoked device {}", session.getId(), deviceId);
            close(session, CLOSE_REVOKED);
            return;
        }

        session.getAttributes().put(DEVICE_ATTR, deviceId);
        WebSocketSession previous = sessions.put(deviceId, session);
        if (previous != null && previous != session) {
            log.info("Board stream device {} superseded session {} with {}",
                    deviceId, previous.getId(), session.getId());
            close(previous, CLOSE_SUPERSEDED);
        }
        log.info("Board stream connected: session={} device={}", session.getId(), deviceId);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        UUID deviceId = (UUID) session.getAttributes().get(DEVICE_ATTR);
        if (deviceId == null) return; // rejected before it was ever registered

        // Two-arg remove: a superseded session closing later must not evict the
        // replacement that has already taken its place.
        sessions.remove(deviceId, session);
        log.info("Board stream disconnected: session={} device={} status={}",
                session.getId(), deviceId, status);
    }

    /** Content every board shares — posters, events, weekly content. */
    public void contentChanged(String reason) {
        push(reason, null);
    }

    /** Config for one board. */
    public void configChanged(UUID deviceId) {
        push("config", deviceId);
    }

    /** Operator-triggered "refresh everything", kept as a manual escape hatch. */
    public void refreshAll() {
        push("manual-refresh", null);
    }

    /**
     * Push a message to every connected board, or to a single one.
     *
     * @param reason why the board should refetch. Carried on the wire for logging and for
     *               a frontend that wants to react selectively.
     * @param target device to notify, or null to reach every board.
     */
    private void push(String reason, UUID target) {
        String json = serialize(reason, target);
        if (json == null) return;

        if (target != null) {
            WebSocketSession session = sessions.get(target);
            if (session == null) {
                log.debug("Board stream reason={} device={} not connected — it will pick the change up on its next poll",
                        reason, target);
                return;
            }
            if (send(target, session, json)) {
                log.debug("Board stream push reason={} device={}", reason, target);
            }
            return;
        }

        int sent = 0;
        for (Map.Entry<UUID, WebSocketSession> entry : sessions.entrySet()) {
            if (send(entry.getKey(), entry.getValue(), json)) sent++;
        }
        log.debug("Board stream broadcast reason={} boards={}", reason, sent);
    }

    /** @return true when the frame was written; drops the session if it has gone away. */
    private boolean send(UUID deviceId, WebSocketSession session, String json) {
        if (!session.isOpen()) {
            sessions.remove(deviceId, session);
            return false;
        }
        try {
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (Exception e) {
            log.warn("Board stream send failed: session={} device={} err={}",
                    session.getId(), deviceId, e.getMessage());
            return false;
        }
    }

    private String serialize(String reason, UUID target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "refresh");
        body.put("reason", reason);
        body.put("deviceId", target);
        // Pre-formatted rather than a raw Instant: a mapper without JavaTimeModule throws on
        // one, and the only symptom would be pushes silently going nowhere.
        body.put("at", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Board stream message could not be serialized", e);
            return null;
        }
    }

    private static UUID parseDeviceId(WebSocketSession session) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (!param.startsWith("deviceId=")) continue;
            String raw = param.substring("deviceId=".length());
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                log.warn("Board stream session={} sent a malformed deviceId: {}", session.getId(), raw);
                return null;
            }
        }
        return null;
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (Exception e) {
            log.debug("Board stream close failed: session={} err={}", session.getId(), e.getMessage());
        }
    }
}
