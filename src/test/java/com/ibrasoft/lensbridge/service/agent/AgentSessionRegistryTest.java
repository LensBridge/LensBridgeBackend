package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentSessionRegistryTest {

    private DeviceEventPublisher events;
    private AgentSessionRegistry registry;

    @BeforeEach
    void setUp() {
        events = mock(DeviceEventPublisher.class);
        registry = new AgentSessionRegistry(events);
    }

    private AgentSession session() {
        WebSocketSession ws = mock(WebSocketSession.class);
        lenient().when(ws.isOpen()).thenReturn(true);
        return new AgentSession(ws, "challenge", new ObjectMapper());
    }

    @Test
    void getReturnsEmptyWhenNoSession() {
        assertThat(registry.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void registerThenGetReturnsSession() {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();

        registry.register(deviceId, s);

        assertThat(registry.get(deviceId)).containsSame(s);
    }

    @Test
    void registerSameSessionTwiceDoesNotEvictItself() throws Exception {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();

        registry.register(deviceId, s);
        registry.register(deviceId, s);

        assertThat(registry.get(deviceId)).containsSame(s);
        verify(s.getTransport(), never()).close(any(CloseStatus.class));
    }

    @Test
    void registerReplacesAndEvictsPriorSession() throws Exception {
        UUID deviceId = UUID.randomUUID();
        AgentSession older = session();
        AgentSession newer = session();

        registry.register(deviceId, older);
        registry.register(deviceId, newer);

        assertThat(registry.get(deviceId)).containsSame(newer);
        verify(older.getTransport()).close(any(CloseStatus.class));
    }

    @Test
    void unregisterRemovesOnlyWhenStillRegisteredAndAnnouncesOffline() {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();
        registry.register(deviceId, s);

        boolean removed = registry.unregister(deviceId, s);

        assertThat(removed).isTrue();
        assertThat(registry.get(deviceId)).isEmpty();
        verify(events).deviceOffline(deviceId);
    }

    @Test
    void unregisterIsNoOpWhenDifferentSessionRegistered() {
        UUID deviceId = UUID.randomUUID();
        AgentSession current = session();
        AgentSession stale = session();
        registry.register(deviceId, current);

        boolean removed = registry.unregister(deviceId, stale);

        assertThat(removed).isFalse();
        assertThat(registry.get(deviceId)).containsSame(current);
        // A superseded session closing late must not report a reconnected device as offline.
        verify(events, never()).deviceOffline(any());
    }

    @Test
    void unregisterTwiceAnnouncesOfflineOnce() {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();
        registry.register(deviceId, s);

        registry.unregister(deviceId, s);
        registry.unregister(deviceId, s);

        verify(events, times(1)).deviceOffline(deviceId);
    }

    @Test
    void closeIfPresentRemovesClosesAndAnnouncesOffline() throws Exception {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();
        registry.register(deviceId, s);

        registry.closeIfPresent(deviceId, CloseStatus.GOING_AWAY);

        assertThat(registry.get(deviceId)).isEmpty();
        verify(s.getTransport()).close(any(CloseStatus.class));
        verify(events).deviceOffline(deviceId);
    }

    @Test
    void closeIfPresentIsNoOpWhenAbsent() {
        UUID deviceId = UUID.randomUUID();

        registry.closeIfPresent(deviceId, CloseStatus.GOING_AWAY);

        assertThat(registry.get(deviceId)).isEmpty();
        verify(events, never()).deviceOffline(any());
    }

    @Test
    void trackedConnectionsAreListedUntilUntracked() {
        AgentSession s = session();

        registry.track(s);
        assertThat(registry.connections()).containsExactly(s);

        registry.untrack(s);
        assertThat(registry.connections()).isEmpty();
    }
}
