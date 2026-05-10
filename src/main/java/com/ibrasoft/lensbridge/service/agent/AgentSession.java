package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.agent.OutgoingAgentFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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

    public enum Phase { UNAUTH, AUTHED, CLOSED }

    @Getter private final UUID sessionId;
    @Getter private final WebSocketSession transport;
    @Getter private final String challenge;

    private final ObjectMapper objectMapper;

    private final AtomicLong outgoingSeq = new AtomicLong(0);

    /** Last seq received from the client (must be strictly increasing). 0 means none yet. */
    private long lastIncomingSeq = 0;

    @Getter private volatile Phase phase = Phase.UNAUTH;
    @Getter private volatile UUID deviceId;

    public AgentSession(WebSocketSession transport, String challenge, ObjectMapper objectMapper) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.sessionId = UUID.randomUUID();
        this.challenge = challenge;
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

    /** Allocates the next outbound seq and returns the populated frame. */
    public OutgoingAgentFrame populateOutgoing(OutgoingAgentFrame.OutgoingAgentFrameBuilder builder) {
        return builder.seq(outgoingSeq.incrementAndGet()).sessionId(sessionId).build();
    }

    public boolean send(OutgoingAgentFrame frame) {
        try {
            String json = objectMapper.writeValueAsString(frame);
            synchronized (transport) {
                if (transport.isOpen()) {
                    transport.sendMessage(new TextMessage(json));
                    return true;
                }
            }
            log.warn("session={} transport is closed; frame {} was not sent", sessionId, frame.getType());
            markClosed();
            return false;
        } catch (JsonProcessingException e) {
            log.error("session={} failed to serialize frame {}", sessionId, frame.getType(), e);
            close(CloseStatus.SERVER_ERROR);
            return false;
        } catch (IOException e) {
            log.warn("session={} transport send failed: {}", sessionId, e.getMessage());
            close(CloseStatus.SESSION_NOT_RELIABLE);
            return false;
        }
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
