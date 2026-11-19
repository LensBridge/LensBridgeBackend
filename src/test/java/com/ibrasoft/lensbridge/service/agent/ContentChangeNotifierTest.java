package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentChangeNotifierTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private AgentSessionRegistry registry;
    private ContentChangeNotifier notifier;

    @BeforeEach
    void setUp() {
        registry = new AgentSessionRegistry(mock(DeviceEventPublisher.class));
        notifier = new ContentChangeNotifier(registry);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AgentSession connected(UUID deviceId) {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        AgentSession session = new AgentSession(ws, "challenge", mapper);
        session.markAuthenticated(deviceId);
        registry.register(deviceId, session);
        return session;
    }

    private JsonNode lastFrame(AgentSession session) throws Exception {
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session.getTransport(), atLeastOnce()).sendMessage(sent.capture());
        return mapper.readTree(sent.getValue().getPayload());
    }

    /** The frame the agent waits for (contract C5): type content_changed, nothing else but the envelope. */
    @Test
    void contentChangedSendsTheFrameToEveryConnectedBoard() throws Exception {
        AgentSession a = connected(UUID.randomUUID());
        AgentSession b = connected(UUID.randomUUID());

        notifier.contentChanged("posters");

        for (AgentSession s : new AgentSession[]{a, b}) {
            JsonNode frame = lastFrame(s);
            assertThat(frame.get("type").asText()).isEqualTo("content_changed");
            assertThat(frame.get("seq").asLong()).isPositive();
            assertThat(frame.get("sessionId").asText()).isEqualTo(s.getSessionId().toString());
            assertThat(frame.size()).as("only type and the frame envelope").isEqualTo(3);
        }
    }

    @Test
    void deviceChangedReachesOnlyThatBoard() throws Exception {
        UUID target = UUID.randomUUID();
        AgentSession mine = connected(target);
        AgentSession other = connected(UUID.randomUUID());

        notifier.deviceChanged(target, "config");

        assertThat(lastFrame(mine).get("type").asText()).isEqualTo("content_changed");
        verify(other.getTransport(), never()).sendMessage(any());
    }

    @Test
    void aBoardThatIsNotConnectedIsSkipped() {
        notifier.deviceChanged(UUID.randomUUID(), "config");
        notifier.contentChanged("events");
        // Nothing to assert beyond "does not throw": there is no session to write to.
    }

    /** Sending inside the transaction would let a board fetch before the change is visible. */
    @Test
    void waitsForTheTransactionToCommit() throws Exception {
        AgentSession s = connected(UUID.randomUUID());
        TransactionSynchronizationManager.initSynchronization();

        notifier.contentChanged("weekly-content");
        verify(s.getTransport(), never()).sendMessage(any());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        assertThat(lastFrame(s).get("type").asText()).isEqualTo("content_changed");
    }
}
