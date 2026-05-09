package com.ibrasoft.lensbridge.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live, authenticated agent sessions keyed by deviceId.
 * <p>
 * A device has at most one live session at a time: if a second session authenticates as
 * the same device, the older one is evicted (assumed to be a stale connection from a
 * NAT-rebound agent reconnecting before the previous TCP session timed out).
 */
@Component
@Slf4j
public class AgentSessionRegistry {

    private final ConcurrentHashMap<UUID, AgentSession> byDevice = new ConcurrentHashMap<>();

    public Optional<AgentSession> get(UUID deviceId) {
        return Optional.ofNullable(byDevice.get(deviceId));
    }

    public void register(UUID deviceId, AgentSession session) {
        AgentSession previous = byDevice.put(deviceId, session);
        if (previous != null && previous != session) {
            log.info("Evicting prior session for device {} (sessionId {}) in favour of {}",
                    deviceId, previous.getSessionId(), session.getSessionId());
            previous.close(CloseStatus.NORMAL.withReason("superseded"));
        }
    }

    /** Removes the session if it is still the registered one for this device. */
    public void unregister(UUID deviceId, AgentSession session) {
        byDevice.remove(deviceId, session);
    }

    public int size() {
        return byDevice.size();
    }
}
