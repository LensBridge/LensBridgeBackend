package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.agent.AuthFrame;
import com.ibrasoft.lensbridge.dto.agent.CommandAckFrame;
import com.ibrasoft.lensbridge.dto.agent.CommandProgressFrame;
import com.ibrasoft.lensbridge.dto.agent.CommandResultFrame;
import com.ibrasoft.lensbridge.dto.agent.HeartbeatFrame;
import com.ibrasoft.lensbridge.dto.agent.IncomingAgentFrame;
import com.ibrasoft.lensbridge.dto.agent.OutgoingAgentFrame;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.AgentSession;
import com.ibrasoft.lensbridge.service.agent.AgentSessionRegistry;
import com.ibrasoft.lensbridge.service.agent.CommandDispatcher;
import com.ibrasoft.lensbridge.service.agent.HeartbeatService;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import com.ibrasoft.lensbridge.service.agent.handshake.AuthSignaturePayload;
import com.ibrasoft.lensbridge.service.agent.handshake.Ed25519Verifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Lifecycle of one agent WebSocket:
 * <pre>
 *   open      → server generates challenge, sends hello (seq:1)
 *   recv auth → verify Ed25519 sig + timestamp + revocation; on success bind deviceId,
 *               send auth_ok (seq:2), register in {@link AgentSessionRegistry}
 *   recv hb   → persist via {@link HeartbeatService}
 *   recv ?    → close 4001 (unauth) / 4003 (bad-state) / 4002 (seq replay)
 *   close     → unregister
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentWebSocketHandler extends TextWebSocketHandler {

    static final String SESSION_ATTR = "agent.session";
    static final long TIMESTAMP_SKEW_MS = 5 * 60 * 1000L;
    static final int CHALLENGE_BYTES = 32;
    static final int HEARTBEAT_INTERVAL_MS = 30_000;

    static final CloseStatus CLOSE_AUTH_FAILED   = new CloseStatus(4001, "auth_failed");
    static final CloseStatus CLOSE_SEQ_VIOLATION = new CloseStatus(4002, "seq_violation");
    static final CloseStatus CLOSE_BAD_STATE     = new CloseStatus(4003, "bad_state");
    static final CloseStatus CLOSE_BAD_FRAME     = new CloseStatus(4004, "bad_frame");

    private final ObjectMapper objectMapper;
    private final Ed25519Verifier ed25519;
    private final DeviceRepository deviceRepository;
    private final AgentSessionRegistry registry;
    private final HeartbeatService heartbeatService;
    private final CommandDispatcher commandDispatcher;
    private final DeviceEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    @Override
    public void afterConnectionEstablished(WebSocketSession transport) throws Exception {
        String challenge = newChallenge();
        AgentSession session = new AgentSession(transport, challenge, objectMapper);
        transport.getAttributes().put(SESSION_ATTR, session);

        OutgoingAgentFrame hello = session.populateOutgoing(OutgoingAgentFrame.builder()
                .type("hello")
                .challenge(challenge)
                .serverTime(System.currentTimeMillis()));
        session.send(hello);
    }

    @Override
    protected void handleTextMessage(WebSocketSession transport, TextMessage message) {
        AgentSession session = (AgentSession) transport.getAttributes().get(SESSION_ATTR);
        if (session == null) {
            safeClose(transport, CLOSE_BAD_STATE);
            return;
        }

        IncomingAgentFrame frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), IncomingAgentFrame.class);
        } catch (Exception e) {
            log.warn("session={} unparseable frame: {}", session.getSessionId(), e.getMessage());
            session.close(CLOSE_BAD_FRAME);
            return;
        }

        if (frame.getSessionId() == null || !frame.getSessionId().equals(session.getSessionId())) {
            log.warn("session={} frame sessionId mismatch (got {})", session.getSessionId(), frame.getSessionId());
            session.close(CLOSE_AUTH_FAILED);
            return;
        }
        if (!session.acceptIncomingSeq(frame.getSeq())) {
            session.close(CLOSE_SEQ_VIOLATION);
            return;
        }

        switch (session.getPhase()) {
            case UNAUTH -> {
                if (frame instanceof AuthFrame auth) {
                    handleAuth(session, auth);
                } else {
                    log.warn("session={} expected auth frame, got {}", session.getSessionId(), frame.getType());
                    session.close(CLOSE_BAD_STATE);
                }
            }
            case AUTHED -> {
                if (frame instanceof HeartbeatFrame hb) {
                    handleHeartbeat(session, hb);
                } else if (frame instanceof CommandAckFrame ack) {
                    commandDispatcher.onAck(ack, session);
                } else if (frame instanceof CommandProgressFrame progress) {
                    commandDispatcher.onProgress(progress, session);
                } else if (frame instanceof CommandResultFrame result) {
                    commandDispatcher.onResult(result, session);
                } else if (frame instanceof AuthFrame) {
                    log.warn("session={} duplicate auth after auth_ok", session.getSessionId());
                    session.close(CLOSE_BAD_STATE);
                } else {
                    log.debug("session={} unhandled frame type={}", session.getSessionId(), frame.getType());
                }
            }
            case CLOSED -> { /* drop */ }
        }
    }

    private void handleAuth(AgentSession session, AuthFrame frame) {
        long now = System.currentTimeMillis();
        if (Math.abs(now - frame.getTimestamp()) > TIMESTAMP_SKEW_MS) {
            log.warn("session={} timestamp skew {} ms (limit {})",
                    session.getSessionId(), now - frame.getTimestamp(), TIMESTAMP_SKEW_MS);
            session.close(CLOSE_AUTH_FAILED);
            return;
        }
        if (frame.getDeviceId() == null || frame.getSignature() == null) {
            session.close(CLOSE_AUTH_FAILED);
            return;
        }

        Optional<Device> deviceOpt = deviceRepository.findById(frame.getDeviceId());
        if (deviceOpt.isEmpty()) {
            log.warn("session={} unknown deviceId {}", session.getSessionId(), frame.getDeviceId());
            session.close(CLOSE_AUTH_FAILED);
            return;
        }
        Device device = deviceOpt.get();
        if (device.getRevokedAt() != null) {
            log.warn("session={} revoked device {} attempting to auth", session.getSessionId(), device.getId());
            session.close(CLOSE_AUTH_FAILED);
            return;
        }
        if (device.getPublicKey() == null) {
            log.error("session={} device {} has no public key on file", session.getSessionId(), device.getId());
            session.close(CLOSE_AUTH_FAILED);
            return;
        }

        byte[] signed = AuthSignaturePayload.build(
                session.getSessionId(),
                session.getChallenge(),
                frame.getDeviceId(),
                frame.getTimestamp());
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(frame.getSignature());
        } catch (IllegalArgumentException e) {
            session.close(CLOSE_AUTH_FAILED);
            return;
        }

        if (!ed25519.verify(device.getPublicKey(), signed, signature)) {
            log.warn("session={} bad Ed25519 signature for device {}", session.getSessionId(), device.getId());
            session.close(CLOSE_AUTH_FAILED);
            return;
        }

        session.markAuthenticated(device.getId());
        registry.register(device.getId(), session);
        log.info("session={} authenticated as device {}", session.getSessionId(), device.getId());

        OutgoingAgentFrame ok = session.populateOutgoing(OutgoingAgentFrame.builder()
                .type("auth_ok")
                .deviceId(device.getId())
                .serverTime(now)
                .heartbeatIntervalMs(HEARTBEAT_INTERVAL_MS));
        session.send(ok);

        events.deviceOnline(device.getId());
        try {
            commandDispatcher.flushPending(device.getId(), session);
        } catch (Exception e) {
            log.error("session={} failed to flush pending commands for device {}",
                    session.getSessionId(), device.getId(), e);
        }
    }

    private void handleHeartbeat(AgentSession session, HeartbeatFrame frame) {
        try {
            String remoteIp = session.getTransport().getRemoteAddress() == null
                    ? null
                    : session.getTransport().getRemoteAddress().getAddress().getHostAddress();
            heartbeatService.record(session.getDeviceId(), frame, remoteIp);
        } catch (Exception e) {
            log.error("session={} heartbeat persistence failed", session.getSessionId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession transport, CloseStatus status) {
        AgentSession session = (AgentSession) transport.getAttributes().get(SESSION_ATTR);
        if (session != null) {
            session.markClosed();
            if (session.getDeviceId() != null) {
                registry.unregister(session.getDeviceId(), session);
                events.deviceOffline(session.getDeviceId());
            }
            log.debug("session={} closed status={}", session.getSessionId(), status);
        }
    }

    private String newChallenge() {
        byte[] raw = new byte[CHALLENGE_BYTES];
        random.nextBytes(raw);
        return Base64.getEncoder().withoutPadding().encodeToString(raw);
    }

    private static void safeClose(WebSocketSession transport, CloseStatus status) {
        try {
            if (transport.isOpen()) transport.close(status);
        } catch (Exception ignored) {
            // best effort
        }
    }
}
