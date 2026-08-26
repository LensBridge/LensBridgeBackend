package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.stream.BoardStreamAuthFrame;
import com.ibrasoft.lensbridge.dto.board.stream.IncomingBoardStreamFrame;
import com.ibrasoft.lensbridge.dto.board.stream.OutgoingBoardStreamFrame;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import com.ibrasoft.lensbridge.service.board.stream.BoardStreamAuthPayload;
import com.ibrasoft.lensbridge.service.board.stream.BoardStreamSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push channel for kiosk boards: tells a board its content or config changed so it can
 * refetch without waiting out its ten-minute poll.
 * <p>
 * Every board on this channel is an enrolled device, and it must <em>prove</em> it — the
 * device id alone is an identifier, not a credential. It is printed in the board's own URL,
 * visible on the physical display, and listed by {@code GET /api/admin/board/devices}, so
 * accepting it as proof let anyone who had ever glanced at a screen evict that kiosk from
 * the stream and read its pushes.
 * <p>
 * The handshake mirrors {@link AgentWebSocketHandler} exactly:
 * <pre>
 *   open      → server generates a 32-byte challenge, sends hello (seq:1)
 *   recv auth → verify Ed25519 sig over sessionId+challenge+deviceId+timestamp, check skew
 *               and revocation; on success bind deviceId and send auth_ok (seq:2)
 *   (no auth) → closed 4008 once the auth deadline passes
 *   recv ?    → close 4001 (auth) / 4002 (seq replay) / 4003 (bad-state) / 4004 (bad frame)
 *   close     → deregister
 * </pre>
 * The verifying public key always comes from the enrolled {@link Device} row; nothing in the
 * incoming frame can influence which key is used.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoardStreamHandler extends TextWebSocketHandler {

    static final int CHALLENGE_BYTES = 32;
    static final long TIMESTAMP_SKEW_MS = 5 * 60 * 1000L;
    /** An auth frame is ~300 characters; anything near this is not a board. */
    static final int MAX_INCOMING_FRAME_CHARS = 4096;

    static final CloseStatus CLOSE_AUTH_FAILED   = new CloseStatus(4001, "auth_failed");
    static final CloseStatus CLOSE_SEQ_VIOLATION = new CloseStatus(4002, "seq_violation");
    static final CloseStatus CLOSE_BAD_STATE     = new CloseStatus(4003, "bad_state");
    static final CloseStatus CLOSE_BAD_FRAME     = new CloseStatus(4004, "bad_frame");
    static final CloseStatus CLOSE_AUTH_TIMEOUT  = new CloseStatus(4008, "auth_timeout");
    static final CloseStatus CLOSE_SUPERSEDED = CloseStatus.NORMAL.withReason("superseded");

    /** Session attribute holding this connection's server-side state. */
    private static final String SESSION_ATTR = "board.stream.session";

    private final ObjectMapper objectMapper;
    private final Ed25519Verifier ed25519;
    private final DeviceRepository deviceRepository;
    private final SecureRandom random = new SecureRandom();

    /**
     * How long a connection may sit unauthenticated before it is closed. Without this an
     * anonymous client could hold sockets open indefinitely and never prove anything.
     */
    @Value("${musallahboard.stream.authTimeoutMs:15000}")
    private long authTimeoutMs = 15_000L;

    /**
     * Authenticated boards keyed by device. One session per device: a reloading kiosk can
     * briefly open its replacement before the old socket finishes closing, so the newcomer
     * wins and the stale one is closed rather than left to accumulate.
     */
    private final Map<UUID, BoardStreamSession> sessions = new ConcurrentHashMap<>();

    /** Connections that have not authenticated yet, keyed by their server-issued session id. */
    private final Map<UUID, BoardStreamSession> pending = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession transport) {
        String challenge = newChallenge();
        BoardStreamSession session = new BoardStreamSession(
                transport, challenge, objectMapper, System.currentTimeMillis() + authTimeoutMs);
        transport.getAttributes().put(SESSION_ATTR, session);
        pending.put(session.getSessionId(), session);

        session.send(OutgoingBoardStreamFrame.builder()
                .type("hello")
                .challenge(challenge)
                .serverTime(System.currentTimeMillis()));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession transport, @NonNull TextMessage message) {
        BoardStreamSession session = (BoardStreamSession) transport.getAttributes().get(SESSION_ATTR);
        if (session == null) {
            safeClose(transport, CLOSE_BAD_STATE);
            return;
        }

        // Nothing this channel accepts is large — an auth frame is a few hundred characters.
        // The container's text buffer is sized for the media paths (10 MB when the customizer
        // is on), which is not a budget an unauthenticated caller should get to spend.
        // Measured in characters rather than bytes on purpose: TextMessage.getPayloadLength()
        // UTF-8-encodes the whole payload to answer, allocating a second copy of exactly the
        // oversized frame we are refusing to spend memory on.
        if (message.getPayload().length() > MAX_INCOMING_FRAME_CHARS) {
            log.warn("board stream session={} sent an oversized frame ({} chars)",
                    session.getSessionId(), message.getPayload().length());
            closeAndDeregister(session, CLOSE_BAD_FRAME);
            return;
        }

        IncomingBoardStreamFrame frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), IncomingBoardStreamFrame.class);
        } catch (Exception e) {
            log.warn("board stream session={} unparseable frame: {}", session.getSessionId(), e.getMessage());
            closeAndDeregister(session, CLOSE_BAD_FRAME);
            return;
        }

        if (frame.getSessionId() == null || !frame.getSessionId().equals(session.getSessionId())) {
            log.warn("board stream session={} frame sessionId mismatch (got {})",
                    session.getSessionId(), frame.getSessionId());
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }
        if (!session.acceptIncomingSeq(frame.getSeq())) {
            closeAndDeregister(session, CLOSE_SEQ_VIOLATION);
            return;
        }

        switch (session.getPhase()) {
            case UNAUTH -> {
                if (frame instanceof BoardStreamAuthFrame auth) {
                    handleAuth(session, auth);
                } else {
                    log.warn("board stream session={} expected auth frame, got {}",
                            session.getSessionId(), frame.getType());
                    closeAndDeregister(session, CLOSE_BAD_STATE);
                }
            }
            case AUTHED -> {
                // Nothing an authenticated board can usefully say on this channel yet; a
                // second auth is a protocol error rather than a re-bind opportunity.
                log.warn("board stream session={} sent {} after auth_ok",
                        session.getSessionId(), frame.getType());
                closeAndDeregister(session, CLOSE_BAD_STATE);
            }
            case CLOSED -> { /* drop */ }
        }
    }

    private void handleAuth(BoardStreamSession session, BoardStreamAuthFrame frame) {
        long now = System.currentTimeMillis();
        if (Math.abs(now - frame.getTimestamp()) > TIMESTAMP_SKEW_MS) {
            log.warn("board stream session={} timestamp skew {} ms (limit {})",
                    session.getSessionId(), now - frame.getTimestamp(), TIMESTAMP_SKEW_MS);
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }
        if (frame.getDeviceId() == null || frame.getSignature() == null) {
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }

        // Unknown, revoked, keyless and badly-signed all close the same way on purpose: the
        // close code must not tell an unauthenticated caller which device ids are real.
        Optional<Device> deviceOpt = deviceRepository.findById(frame.getDeviceId());
        if (deviceOpt.isEmpty()) {
            log.warn("board stream session={} named unknown device {}", session.getSessionId(), frame.getDeviceId());
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }
        Device device = deviceOpt.get();
        if (device.getRevokedAt() != null) {
            log.warn("board stream session={} revoked device {} attempting to auth",
                    session.getSessionId(), device.getId());
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }
        if (device.getPublicKey() == null) {
            log.error("board stream session={} device {} has no public key on file",
                    session.getSessionId(), device.getId());
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }

        byte[] signed = BoardStreamAuthPayload.build(
                session.getSessionId(),
                session.getChallenge(),
                frame.getDeviceId(),
                frame.getTimestamp());
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(frame.getSignature());
        } catch (IllegalArgumentException e) {
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }

        if (!ed25519.verify(device.getPublicKey(), signed, signature)) {
            log.warn("board stream session={} bad Ed25519 signature for device {}",
                    session.getSessionId(), device.getId());
            closeAndDeregister(session, CLOSE_AUTH_FAILED);
            return;
        }

        if (!session.markAuthenticated(device.getId())) {
            // The transport dropped while we were verifying; the close callback already ran.
            log.debug("board stream session={} closed before auth could be bound", session.getSessionId());
            deregister(session);
            return;
        }
        pending.remove(session.getSessionId(), session);

        BoardStreamSession previous = sessions.put(device.getId(), session);
        if (previous != null && previous != session) {
            log.info("board stream device {} superseded session {} with {}",
                    device.getId(), previous.getSessionId(), session.getSessionId());
            previous.close(CLOSE_SUPERSEDED);
        }
        log.info("board stream session={} authenticated as device {}", session.getSessionId(), device.getId());

        session.send(OutgoingBoardStreamFrame.builder()
                .type("auth_ok")
                .deviceId(device.getId())
                .serverTime(now));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession transport, @NonNull CloseStatus status) {
        BoardStreamSession session = (BoardStreamSession) transport.getAttributes().get(SESSION_ATTR);
        if (session == null) return;

        session.markClosed();
        deregister(session);
        log.info("board stream disconnected: session={} device={} status={}",
                session.getSessionId(), session.getDeviceId(), status);
    }

    /**
     * Closes connections that connected but never proved who they were. Runs on a timer
     * rather than a per-session task so one sweep covers the whole fleet.
     */
    @Scheduled(fixedDelayString = "${musallahboard.stream.authReaperIntervalMs:5000}")
    public void reapUnauthenticatedSessions() {
        long now = System.currentTimeMillis();
        for (BoardStreamSession session : pending.values()) {
            if (session.getPhase() == BoardStreamSession.Phase.UNAUTH && now > session.getAuthDeadlineMs()) {
                log.warn("board stream session={} never authenticated within {} ms; closing",
                        session.getSessionId(), authTimeoutMs);
                closeAndDeregister(session, CLOSE_AUTH_TIMEOUT);
            } else if (session.getPhase() == BoardStreamSession.Phase.CLOSED) {
                pending.remove(session.getSessionId(), session);
            }
        }
    }

    /**
     * Closes a device's live stream session, if it has one.
     * <p>
     * Revocation is checked at handshake time, so without this a board revoked mid-session
     * would keep receiving pushes until it happened to reconnect. The agent channel already
     * closes on revoke via {@code AgentSessionRegistry.closeIfPresent}; now that this channel
     * also binds a device identity, it owes the same guarantee.
     */
    public void closeIfPresent(UUID deviceId, CloseStatus status) {
        BoardStreamSession session = sessions.remove(deviceId);
        if (session != null) {
            log.info("board stream closing session {} for device {}", session.getSessionId(), deviceId);
            session.close(status);
        }
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
     * Push a message to every authenticated board, or to a single one.
     *
     * @param reason why the board should refetch. Carried on the wire for logging and for
     *               a frontend that wants to react selectively.
     * @param target device to notify, or null to reach every board.
     */
    private void push(String reason, UUID target) {
        String at = Instant.now().toString();

        if (target != null) {
            BoardStreamSession session = sessions.get(target);
            if (session == null) {
                log.debug("board stream reason={} device={} not connected — it will pick the change up on its next poll",
                        reason, target);
                return;
            }
            if (send(target, session, reason, target, at)) {
                log.debug("board stream push reason={} device={}", reason, target);
            }
            return;
        }

        int sent = 0;
        for (Map.Entry<UUID, BoardStreamSession> entry : sessions.entrySet()) {
            if (send(entry.getKey(), entry.getValue(), reason, null, at)) sent++;
        }
        log.debug("board stream broadcast reason={} boards={}", reason, sent);
    }

    /** @return true when the frame was written; drops the session if it has gone away. */
    private boolean send(UUID deviceId, BoardStreamSession session, String reason, UUID target, String at) {
        if (!session.getTransport().isOpen()) {
            sessions.remove(deviceId, session);
            return false;
        }
        return session.send(OutgoingBoardStreamFrame.builder()
                .type("refresh")
                .reason(reason)
                .deviceId(target)
                .at(at));
    }

    private void closeAndDeregister(BoardStreamSession session, CloseStatus status) {
        session.close(status);
        deregister(session);
    }

    /**
     * Two-arg removes throughout: a superseded session closing later must not evict the
     * replacement that has already taken its place.
     */
    private void deregister(BoardStreamSession session) {
        pending.remove(session.getSessionId(), session);
        UUID deviceId = session.getDeviceId();
        if (deviceId != null) sessions.remove(deviceId, session);
    }

    private String newChallenge() {
        byte[] raw = new byte[CHALLENGE_BYTES];
        random.nextBytes(raw);
        return Base64.getEncoder().withoutPadding().encodeToString(raw);
    }

    private static void safeClose(WebSocketSession transport, CloseStatus status) {
        try {
            if (transport.isOpen()) transport.close(status);
        } catch (Exception e) {
            log.debug("board stream close failed: session={} err={}", transport.getId(), e.getMessage());
        }
    }
}
