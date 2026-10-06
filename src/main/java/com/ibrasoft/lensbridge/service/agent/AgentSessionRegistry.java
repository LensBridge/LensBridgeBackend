package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live agent sessions: the authenticated ones keyed by deviceId, plus every open
 * connection (authenticated or not) so a sweep can find sockets that never authenticate or
 * have gone quiet.
 * <p>
 * A device has at most one live session at a time: if a second session authenticates as
 * the same device, the older one is evicted (assumed to be a stale connection from a
 * NAT-rebound agent reconnecting before the previous TCP session timed out).
 * <p>
 * The registry also announces when a device goes offline, and only from the code path that
 * actually removes its session. Announcing from the socket's close callback instead would
 * report a reconnected device as offline: the evicted session closes after its replacement
 * is already registered, and that is not an outage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentSessionRegistry {

    private final DeviceEventPublisher events;

    private final ConcurrentHashMap<UUID, AgentSession> byDevice = new ConcurrentHashMap<>();
    private final Set<AgentSession> connections = ConcurrentHashMap.newKeySet();

    public Optional<AgentSession> get(UUID deviceId) {
        return Optional.ofNullable(byDevice.get(deviceId));
    }

    /** Starts tracking a freshly opened connection, before it has proved who it is. */
    public void track(AgentSession session) {
        connections.add(session);
    }

    public void untrack(AgentSession session) {
        connections.remove(session);
    }

    /** A snapshot of every open connection, for the liveness sweep. */
    public List<AgentSession> connections() {
        return List.copyOf(connections);
    }

    public void register(UUID deviceId, AgentSession session) {
        AgentSession previous = byDevice.put(deviceId, session);
        if (previous != null && previous != session) {
            log.info("Evicting prior session for device {} (sessionId {}) in favour of {}",
                    deviceId, previous.getSessionId(), session.getSessionId());
            previous.close(CloseStatus.NORMAL.withReason("superseded"));
        }
    }

    /**
     * Removes the session if it is still the registered one for this device, and reports the
     * device offline in that case only.
     *
     * @return true when this call removed the session (false: it was already replaced or gone)
     */
    public boolean unregister(UUID deviceId, AgentSession session) {
        boolean removed = byDevice.remove(deviceId, session);
        if (removed) {
            events.deviceOffline(deviceId);
        }
        return removed;
    }

    public void closeIfPresent(UUID deviceId, CloseStatus status) {
        AgentSession session = byDevice.remove(deviceId);
        if (session != null) {
            log.info("Closing session {} for device {}", session.getSessionId(), deviceId);
            events.deviceOffline(deviceId);
            session.close(status);
        }
    }
}
