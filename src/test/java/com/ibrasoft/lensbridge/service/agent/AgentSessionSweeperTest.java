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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentSessionSweeperTest {

    private static final long AUTH_TIMEOUT_MS = 15_000;
    private static final long IDLE_TIMEOUT_MS = 90_000;

    private DeviceEventPublisher events;
    private AgentSessionRegistry registry;
    private AgentSessionSweeper sweeper;

    @BeforeEach
    void setUp() {
        events = mock(DeviceEventPublisher.class);
        registry = new AgentSessionRegistry(events);
        sweeper = new AgentSessionSweeper(registry, AUTH_TIMEOUT_MS, IDLE_TIMEOUT_MS);
    }

    @Test
    void closesASocketThatNeverAuthenticates() throws Exception {
        AgentSession s = tracked();

        sweeper.sweep(s.getConnectedAtMs() + AUTH_TIMEOUT_MS + 1);

        verify(s.getTransport()).close(AgentSessionSweeper.CLOSE_AUTH_TIMEOUT);
        assertThat(registry.connections()).isEmpty();
        verify(events, never()).deviceOffline(any());
    }

    @Test
    void leavesAFreshUnauthenticatedSocketAlone() throws Exception {
        AgentSession s = tracked();

        sweeper.sweep(s.getConnectedAtMs() + AUTH_TIMEOUT_MS - 1);

        verify(s.getTransport(), never()).close(any(CloseStatus.class));
        assertThat(registry.connections()).containsExactly(s);
    }

    @Test
    void doesNotApplyTheAuthDeadlineToAnAuthenticatedSession() throws Exception {
        AgentSession s = tracked();
        s.markAuthenticated(UUID.randomUUID());
        registry.register(s.getDeviceId(), s);

        // Well past the auth deadline but nowhere near the idle timeout.
        sweeper.sweep(s.getConnectedAtMs() + AUTH_TIMEOUT_MS * 2);

        verify(s.getTransport(), never()).close(any(CloseStatus.class));
        assertThat(registry.get(s.getDeviceId())).containsSame(s);
    }

    @Test
    void dropsAnAuthenticatedSessionThatWentSilentAndReportsTheDeviceOffline() throws Exception {
        AgentSession s = tracked();
        UUID deviceId = UUID.randomUUID();
        s.markAuthenticated(deviceId);
        registry.register(deviceId, s);

        sweeper.sweep(s.getLastInboundAtMs() + IDLE_TIMEOUT_MS + 1);

        verify(s.getTransport()).close(AgentSessionSweeper.CLOSE_IDLE_TIMEOUT);
        // Unregistered by the sweep itself, so commands stop being delivered to it at once.
        assertThat(registry.get(deviceId)).isEmpty();
        verify(events).deviceOffline(deviceId);
        assertThat(registry.connections()).isEmpty();
    }

    @Test
    void aSessionThatKeepsTalkingSurvives() throws Exception {
        AgentSession s = tracked();
        UUID deviceId = UUID.randomUUID();
        s.markAuthenticated(deviceId);
        registry.register(deviceId, s);
        s.touch();

        sweeper.sweep(s.getLastInboundAtMs() + IDLE_TIMEOUT_MS - 1);

        verify(s.getTransport(), never()).close(any(CloseStatus.class));
        assertThat(registry.get(deviceId)).containsSame(s);
    }

    @Test
    void anIdleSessionAlreadyReplacedByAReconnectDoesNotReportTheDeviceOffline() throws Exception {
        UUID deviceId = UUID.randomUUID();
        AgentSession stale = tracked();
        stale.markAuthenticated(deviceId);
        registry.register(deviceId, stale);
        AgentSession fresh = tracked();
        fresh.markAuthenticated(deviceId);
        registry.register(deviceId, fresh);
        Thread.sleep(5);
        fresh.touch(); // the live replacement is talking; only the old socket is idle

        sweeper.sweep(stale.getLastInboundAtMs() + IDLE_TIMEOUT_MS + 1);

        verify(events, never()).deviceOffline(any());
        assertThat(registry.get(deviceId)).containsSame(fresh);
    }

    @Test
    void forgetsConnectionsThatAreAlreadyClosed() {
        AgentSession s = tracked();
        s.markClosed();

        sweeper.sweep(s.getConnectedAtMs());

        assertThat(registry.connections()).isEmpty();
    }

    private AgentSession tracked() {
        WebSocketSession ws = mock(WebSocketSession.class);
        lenient().when(ws.isOpen()).thenReturn(true);
        AgentSession session = new AgentSession(ws, "challenge", new ObjectMapper());
        registry.track(session);
        return session;
    }
}
