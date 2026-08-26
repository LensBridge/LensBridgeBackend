package com.ibrasoft.lensbridge.service.board.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.stream.OutgoingBoardStreamFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side state for one open board refresh-stream connection.
 * <p>
 * Mirrors {@link com.ibrasoft.lensbridge.service.agent.AgentSession}: a connection carries no
 * authority until an Ed25519 challenge–response succeeds, and the device it speaks for is
 * decided here, not by anything the client asserts in a URL.
 * <ol>
 *   <li><b>UNAUTH</b> — connected, server has issued a {@code hello} with a challenge. Only an
 *       {@code auth} frame is allowed, and only until {@code authDeadline} passes.</li>
 *   <li><b>AUTHED</b> — signature verified; {@link #deviceId} is bound and the session is
 *       eligible to receive pushes.</li>
 *   <li><b>CLOSED</b> — underlying transport closed.</li>
 * </ol>
 */
@Slf4j
public class BoardStreamSession {

    public enum Phase { UNAUTH, AUTHED, CLOSED }

    @Getter private final UUID sessionId;
    @Getter private final WebSocketSession transport;
    @Getter private final String challenge;
    /** Epoch millis after which an unauthenticated session is reaped. */
    @Getter private final long authDeadlineMs;

    private final ObjectMapper objectMapper;

    private final AtomicLong outgoingSeq = new AtomicLong(0);

    /** Last seq received from the client (must be strictly increasing). 0 means none yet. */
    private long lastIncomingSeq = 0;

    @Getter private volatile Phase phase = Phase.UNAUTH;
    @Getter private volatile UUID deviceId;

    public BoardStreamSession(WebSocketSession transport,
                              String challenge,
                              ObjectMapper objectMapper,
                              long authDeadlineMs) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.sessionId = UUID.randomUUID();
        this.challenge = challenge;
        this.authDeadlineMs = authDeadlineMs;
    }

    /** Rejects replayed or regressed sequence numbers. */
    public synchronized boolean acceptIncomingSeq(long seq) {
        if (seq <= lastIncomingSeq) {
            log.warn("board stream session={} rejecting non-monotonic seq {} (last seen {})",
                    sessionId, seq, lastIncomingSeq);
            return false;
        }
        lastIncomingSeq = seq;
        return true;
    }

    /**
     * Binds the device, but only from UNAUTH.
     * <p>
     * Returns false rather than throwing: the transport can drop while the handshake is
     * mid-verification, in which case the close callback has already moved this session to
     * CLOSED and the caller simply has nothing left to do. Throwing there would escape
     * {@code handleTextMessage} as an unchecked error for an entirely ordinary disconnect.
     *
     * @return true if this call performed the transition
     */
    public synchronized boolean markAuthenticated(UUID deviceId) {
        if (phase != Phase.UNAUTH) return false;
        this.deviceId = deviceId;
        this.phase = Phase.AUTHED;
        return true;
    }

    public synchronized void markClosed() {
        this.phase = Phase.CLOSED;
    }

    /**
     * Allocates the next outbound seq and writes the frame.
     * <p>
     * Seq allocation happens <em>inside</em> the transport monitor, not before it. Two request
     * threads can push to the same board at once (a poster edit and a config change), and
     * allocating outside the lock lets them interleave so that seq 5 reaches the wire ahead
     * of seq 4 — which is exactly the regression a client validating the sequence would
     * reconnect over.
     *
     * @return true when the frame reached the wire.
     */
    public boolean send(OutgoingBoardStreamFrame.OutgoingBoardStreamFrameBuilder builder) {
        String type = null;
        synchronized (transport) {
            if (!transport.isOpen()) {
                log.debug("board stream session={} transport closed; frame was not sent", sessionId);
                markClosed();
                return false;
            }
            OutgoingBoardStreamFrame frame = builder
                    .seq(outgoingSeq.incrementAndGet())
                    .sessionId(sessionId)
                    .build();
            type = frame.getType();
            try {
                transport.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                return true;
            } catch (JsonProcessingException e) {
                log.error("board stream session={} failed to serialize frame {}", sessionId, type, e);
                close(CloseStatus.SERVER_ERROR);
                return false;
            } catch (IOException e) {
                log.warn("board stream session={} transport send failed: {}", sessionId, e.getMessage());
                close(CloseStatus.SESSION_NOT_RELIABLE);
                return false;
            } catch (Exception e) {
                // Tomcat's endpoint throws unchecked IllegalStateException when the socket
                // moves to a bad state between the isOpen() check and the write. One dying
                // board must not abort a fleet-wide broadcast, nor fail the admin's HTTP
                // request that triggered it.
                log.warn("board stream session={} send failed on frame {}: {}", sessionId, type, e.toString());
                close(CloseStatus.SESSION_NOT_RELIABLE);
                return false;
            }
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
