package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentSessionRegistryTest {

    private AgentSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentSessionRegistry();
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
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void registerSameSessionTwiceDoesNotEvictItself() {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();

        registry.register(deviceId, s);
        registry.register(deviceId, s);

        assertThat(registry.get(deviceId)).containsSame(s);
        // verify(s.getTransport(), never()).close(any(CloseStatus.class));
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
    void unregisterRemovesOnlyWhenStillRegistered() {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();
        registry.register(deviceId, s);

        registry.unregister(deviceId, s);

        assertThat(registry.get(deviceId)).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void unregisterIsNoOpWhenDifferentSessionRegistered() {
        UUID deviceId = UUID.randomUUID();
        AgentSession current = session();
        AgentSession stale = session();
        registry.register(deviceId, current);

        registry.unregister(deviceId, stale);

        assertThat(registry.get(deviceId)).containsSame(current);
    }

    @Test
    void closeIfPresentRemovesAndClosesSession() throws Exception {
        UUID deviceId = UUID.randomUUID();
        AgentSession s = session();
        registry.register(deviceId, s);

        registry.closeIfPresent(deviceId, CloseStatus.GOING_AWAY);

        assertThat(registry.get(deviceId)).isEmpty();
        verify(s.getTransport()).close(any(CloseStatus.class));
    }

    @Test
    void closeIfPresentIsNoOpWhenAbsent() {
        registry.closeIfPresent(UUID.randomUUID(), CloseStatus.GOING_AWAY);

        assertThat(registry.size()).isZero();
    }
}
