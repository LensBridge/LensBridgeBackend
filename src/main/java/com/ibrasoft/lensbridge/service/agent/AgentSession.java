package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.agent.OutgoingAgentFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;

/**
 * Server-side state for one open agent WebSocket connection.
 * <p>
 * A session moves through three phases:
 * <ol>
 *   <li><b>UNAUTH</b> — connected, server has issued a {@code hello} with a challenge,
 *       but the client has not yet authenticated. Only an {@code auth} frame is allowed.</li>
 *   <li><b>AUTHED</b> — Ed25519 challenge–response succeeded; {@link #deviceId} is bound.</li>
 *   <li><b>CLOSED</b> — underlying transport closed.</li>
 * </ol>
 */
@Slf4j
public class AgentSession {

    /** How often an authenticated agent sends a heartbeat; also what auth_ok tells it to use. */
    public static final int HEARTBEAT_INTERVAL_MS = 30_000;

    public enum Phase { UNAUTH, AUTHED, CLOSED }

    @Getter private final UUID sessionId;
    @Getter private final WebSocketSession transport;
    @Getter private final String challenge;

    private final ObjectMapper objectMapper;

    /** Guarded by the transport lock in {@link #send}; see there for why. */
    private long outgoingSeq = 0;

    /** Last seq received from the client (must be strictly increasing). 0 means none yet. */
    private long lastIncomingSeq = 0;

    @Getter private volatile Phase phase = Phase.UNAUTH;
    @Getter private volatile UUID deviceId;

    /** When the socket opened (epoch millis); the clock for the authentication deadline. */
    @Getter private final long connectedAtMs = System.currentTimeMillis();

    /** When the last frame arrived (epoch millis); the clock for the idle timeout. */
    @Getter private volatile long lastInboundAtMs = connectedAtMs;

    public AgentSession(WebSocketSession transport, String challenge, ObjectMapper objectMapper) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.sessionId = UUID.randomUUID();
        this.challenge = challenge;
    }

    /** Notes that the peer just sent something, so a live connection is not swept as idle. */
    public void touch() {
        lastInboundAtMs = System.currentTimeMillis();
    }

    /** Records that the next valid incoming seq is {@code seq + 1}. Returns false if this seq is a replay/regression. */
    public synchronized boolean acceptIncomingSeq(long seq) {
        if (seq <= lastIncomingSeq) {
            log.warn("session={} rejecting non-monotonic seq {} (last seen {})", sessionId, seq, lastIncomingSeq);
            return false;
        }
        lastIncomingSeq = seq;
        return true;
    }

    public synchronized void markAuthenticated(UUID deviceId) {
        if (phase != Phase.UNAUTH) {
            throw new IllegalStateException("Session already past UNAUTH phase: " + phase);
        }
        this.deviceId = deviceId;
        this.phase = Phase.AUTHED;
    }

    public synchronized void markClosed() {
        this.phase = Phase.CLOSED;
    }

    /**
     * Allocates the next outbound seq and writes the frame, as one step under the transport
     * lock. Allocating before taking the lock would let two threads number their frames in one
     * order and write them in the other, and the agent expects them to arrive in seq order.
     * A frame that cannot be written (closed transport) does not consume a seq.
     *
     * @return true when the frame was handed to the transport
     */
    public boolean send(OutgoingAgentFrame.OutgoingAgentFrameBuilder builder) {
        CloseStatus failure = null;
        try {
            synchronized (transport) {
                if (!transport.isOpen()) {
                    log.warn("session={} transport is closed; outgoing frame was not sent", sessionId);
                    markClosed();
                    return false;
                }
                OutgoingAgentFrame frame = builder.seq(++outgoingSeq).sessionId(sessionId).build();
                try {
                    transport.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                    return true;
                } catch (JsonProcessingException e) {
                    log.error("session={} failed to serialize outgoing frame", sessionId, e);
                    failure = CloseStatus.SERVER_ERROR;
                } catch (IOException e) {
                    log.warn("session={} transport send failed: {}", sessionId, e.getMessage());
                    failure = CloseStatus.SESSION_NOT_RELIABLE;
                }
            }
        } finally {
            // Outside the lock: closing a transport can block on the peer.
            if (failure != null) close(failure);
        }
        return false;
    }

    public void close(CloseStatus status) {
        try {
            if (transport.isOpen()) transport.close(status);
        } catch (IOException ignored) {
            // best effort
        } finally {
            markClosed();
        }
    }
}
