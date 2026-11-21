package com.ibrasoft.lensbridge.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * Closes agent sockets that are alive on paper only.
 * <p>
 * Two kinds exist, and neither produces a frame, so the WebSocket handler never gets a chance
 * to act on them:
 * <ul>
 *   <li><b>Never authenticated.</b> Anyone can open the upgrade (authentication happens inside
 *       the channel). Without a deadline a client can sit in UNAUTH forever, and the message
 *       buffer is sized for screenshots, so each idle socket is a standing cost.</li>
 *   <li><b>Authenticated but silent.</b> A device that lost power or its network leaves a
 *       half-open TCP connection that the server only notices much later, if ever. Until then
 *       it stays registered, and commands are "delivered" into the void and marked DELIVERED.
 *       A live agent sends a heartbeat every {@link AgentSession#HEARTBEAT_INTERVAL_MS}, so
 *       several missed beats mean it is gone.</li>
 * </ul>
 * Removing an authenticated session here goes through {@link AgentSessionRegistry#unregister},
 * which is also what announces the device offline; the socket's own close callback may be
 * very late for a half-open connection, or never arrive.
 */
@Component
@Slf4j
public class AgentSessionSweeper {

    static final CloseStatus CLOSE_AUTH_TIMEOUT = new CloseStatus(4001, "auth_timeout");
    static final CloseStatus CLOSE_IDLE_TIMEOUT = new CloseStatus(4005, "idle_timeout");

    private final AgentSessionRegistry registry;
    private final long authTimeoutMs;
    private final long idleTimeoutMs;

    /**
     * @param authTimeoutMs how long a socket may stay unauthenticated; the agent authenticates
     *                      within a round trip of the hello, so this is generous
     * @param idleTimeoutMs how long an authenticated session may go without any inbound frame;
     *                      the default is three heartbeat intervals
     */
    public AgentSessionSweeper(
            AgentSessionRegistry registry,
            @Value("${musallahboard.agent.authTimeoutMs:15000}") long authTimeoutMs,
            @Value("${musallahboard.agent.idleTimeoutMs:" + (3L * AgentSession.HEARTBEAT_INTERVAL_MS) + "}")
            long idleTimeoutMs) {
        this.registry = registry;
        this.authTimeoutMs = authTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${musallahboard.agent.sweepIntervalMs:5000}")
    public void sweep() {
        sweep(System.currentTimeMillis());
    }

    void sweep(long nowMs) {
        for (AgentSession session : registry.connections()) {
            switch (session.getPhase()) {
                case UNAUTH -> {
                    if (nowMs - session.getConnectedAtMs() > authTimeoutMs) {
                        log.info("session={} did not authenticate within {} ms; closing",
                                session.getSessionId(), authTimeoutMs);
                        drop(session, CLOSE_AUTH_TIMEOUT);
                    }
                }
                case AUTHED -> {
                    if (nowMs - session.getLastInboundAtMs() > idleTimeoutMs) {
                        log.warn("session={} device={} sent nothing for over {} ms; closing as half-open",
                                session.getSessionId(), session.getDeviceId(), idleTimeoutMs);
                        drop(session, CLOSE_IDLE_TIMEOUT);
                    }
                }
                // Already closed but never untracked (its close callback did not run): forget it.
                case CLOSED -> registry.untrack(session);
            }
        }
    }

    private void drop(AgentSession session, CloseStatus status) {
        registry.untrack(session);
        if (session.getDeviceId() != null) {
            registry.unregister(session.getDeviceId(), session);
        }
        session.close(status);
    }
}
