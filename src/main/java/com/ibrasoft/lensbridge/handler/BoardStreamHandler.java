package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push channel for board agents: tells a board its content or config changed, so the agent
 * syncs content ten seconds later instead of waiting for its 30-minute schedule (agent
 * {@code docs/architecture.md}, section 9.1). A missed push only costs that wait.
 * <p>
 * Every board on this channel is an enrolled, unrevoked device; there is no anonymous or
 * pre-enrollment state here. Who the device is comes from the upgrade request:
 * <ul>
 *   <li><b>Signed</b>: {@link BoardStreamHandshakeInterceptor} verified the device's
 *       {@code X-MB-*} signature and recorded the device it proved.</li>
 *   <li><b>Unsigned</b>, from an agent that predates signing, and only while
 *       {@code musallahboard.board-stream.require-signature} is false: the device named by
 *       {@code ?deviceId=<uuid>}. Anyone who knows the id can make that claim, so an unsigned
 *       connection never displaces a signed one for the same device.</li>
 * </ul>
 * Revoking a device closes its connection here ({@link #disconnect(UUID)}), as it does the
 * agent's own socket.
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
    /** An unsigned connection for a device that already holds a signed one. */
    static final CloseStatus CLOSE_SIGNED_SESSION_ACTIVE = new CloseStatus(4004, "signed_session_active");

    /**
     * A push that has been stuck this long means the board has stopped reading; the decorator
     * then closes the session instead of letting writers pile up behind it. Pushes are tiny
     * and a healthy board drains them instantly, so this is generous.
     */
    static final int SEND_TIME_LIMIT_MS = 10_000;

    /** Bytes of unsent pushes tolerated per board before it is cut off (a push is ~100 bytes). */
    static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    /** Session attribute holding the device a connection was accepted as. */
    private static final String DEVICE_ATTR = "board.deviceId";

    private final ObjectMapper objectMapper;
    private final DeviceRepository deviceRepository;

    /**
     * Live boards keyed by device. One session per device: a reconnecting agent can open its
     * replacement before the server notices the old socket is gone, so the newcomer wins
     * and the stale one is closed rather than left to accumulate, except that an unsigned
     * newcomer never wins over a signed session.
     * <p>
     * Sessions are stored wrapped in a {@link ConcurrentWebSocketSessionDecorator}. Pushes
     * come from whichever admin request thread changed the content, and the container allows
     * only one writer per session at a time: a second concurrent send throws, and the
     * failure was being swallowed, so a board silently missed its refresh. The decorator
     * serialises the writes.
     */
    private final Map<UUID, Board> sessions = new ConcurrentHashMap<>();

    /** A registered connection, and whether its upgrade carried a verified device signature. */
    private record Board(ConcurrentWebSocketSessionDecorator session, boolean signed) {
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        UUID signedDevice = (UUID) session.getAttributes().get(BoardStreamHandshakeInterceptor.SIGNED_DEVICE_ATTR);
        boolean signed = signedDevice != null;
        UUID deviceId = signed ? signedDevice : parseDeviceId(session.getUri());
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

        ConcurrentWebSocketSessionDecorator safe =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        Board incoming = new Board(safe, signed);
        Board[] displaced = {null};
        Board current = sessions.compute(deviceId, (id, existing) -> {
            if (!signed && existing != null && existing.signed() && existing.session().isOpen()) {
                return existing;
            }
            displaced[0] = existing;
            return incoming;
        });
        if (current != incoming) {
            log.warn("Board stream refused unsigned session={} for device {}: it holds signed session {}",
                    session.getId(), deviceId, current.session().getId());
            close(session, CLOSE_SIGNED_SESSION_ACTIVE);
            return;
        }
        session.getAttributes().put(DEVICE_ATTR, deviceId);
        if (displaced[0] != null) {
            log.info("Board stream device {} superseded session {} with {}",
                    deviceId, displaced[0].session().getId(), session.getId());
            close(displaced[0].session(), CLOSE_SUPERSEDED);
        }
        log.info("Board stream connected: session={} device={} signed={}", session.getId(), deviceId, signed);
    }

    /**
     * Closes the device's connection, if it has one, for a revoke. Its reconnect is refused:
     * a revoked device fails both the signature check and the enrollment check.
     */
    public void disconnect(UUID deviceId) {
        Board board = sessions.remove(deviceId);
        if (board == null) return;
        log.info("Board stream closing session={} device={}: device revoked", board.session().getId(), deviceId);
        close(board.session(), CLOSE_REVOKED);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        UUID deviceId = (UUID) session.getAttributes().get(DEVICE_ATTR);
        if (deviceId == null) return; // rejected before it was ever registered

        // Only remove the entry if it still wraps this very session: a superseded session
        // closing later must not evict the replacement that has already taken its place.
        sessions.computeIfPresent(deviceId, (id, current) -> current.session().getDelegate() == session ? null : current);
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
            Board board = sessions.get(target);
            if (board == null) {
                log.debug("Board stream reason={} device={} not connected — it will pick the change up on its next sync",
                        reason, target);
                return;
            }
            if (send(target, board, json)) {
                log.debug("Board stream push reason={} device={}", reason, target);
            }
            return;
        }

        int sent = 0;
        for (Map.Entry<UUID, Board> entry : sessions.entrySet()) {
            if (send(entry.getKey(), entry.getValue(), json)) sent++;
        }
        log.debug("Board stream broadcast reason={} boards={}", reason, sent);
    }

    /** @return true when the frame was written; drops the session if it has gone away. */
    private boolean send(UUID deviceId, Board board, String json) {
        ConcurrentWebSocketSessionDecorator session = board.session();
        if (!session.isOpen()) {
            sessions.remove(deviceId, board);
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

    /** The {@code deviceId} query parameter, or null when it is missing or not a UUID. */
    static UUID parseDeviceId(URI uri) {
        String query = uri == null ? null : uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (!param.startsWith("deviceId=")) continue;
            String raw = param.substring("deviceId=".length());
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                log.warn("Board stream upgrade sent a malformed deviceId");
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
