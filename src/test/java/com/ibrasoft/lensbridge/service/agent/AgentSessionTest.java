package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.agent.OutgoingAgentFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentSessionTest {

    private WebSocketSession transport;
    private AgentSession session;

    @BeforeEach
    void setUp() {
        transport = mock(WebSocketSession.class);
        lenient().when(transport.isOpen()).thenReturn(true);
        session = new AgentSession(transport, "the-challenge", new ObjectMapper());
    }

    @Test
    void newSessionStartsUnauthWithIdentifiers() {
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.UNAUTH);
        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.getChallenge()).isEqualTo("the-challenge");
        assertThat(session.getDeviceId()).isNull();
    }

    @Test
    void acceptIncomingSeqRequiresStrictlyIncreasing() {
        assertThat(session.acceptIncomingSeq(1)).isTrue();
        assertThat(session.acceptIncomingSeq(2)).isTrue();
        assertThat(session.acceptIncomingSeq(2)).isFalse();
        assertThat(session.acceptIncomingSeq(1)).isFalse();
        assertThat(session.acceptIncomingSeq(3)).isTrue();
    }

    @Test
    void markAuthenticatedBindsDeviceAndAdvancesPhase() {
        UUID deviceId = UUID.randomUUID();

        session.markAuthenticated(deviceId);

        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.AUTHED);
        assertThat(session.getDeviceId()).isEqualTo(deviceId);
    }

    @Test
    void markAuthenticatedRejectedWhenNotUnauth() {
        session.markAuthenticated(UUID.randomUUID());

        assertThatThrownBy(() -> session.markAuthenticated(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markClosedSetsClosedPhase() {
        session.markClosed();

        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }

    @Test
    void populateOutgoingAllocatesMonotonicSeqAndSessionId() {
        OutgoingAgentFrame f1 = session.populateOutgoing(OutgoingAgentFrame.builder().type("hello"));
        OutgoingAgentFrame f2 = session.populateOutgoing(OutgoingAgentFrame.builder().type("command"));

        assertThat(f1.getSeq()).isEqualTo(1L);
        assertThat(f2.getSeq()).isEqualTo(2L);
        assertThat(f1.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(f2.getSessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    void sendWritesMessageWhenTransportOpen() throws Exception {
        OutgoingAgentFrame frame = session.populateOutgoing(OutgoingAgentFrame.builder().type("hello"));

        boolean sent = session.send(frame);

        assertThat(sent).isTrue();
        verify(transport).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendReturnsFalseAndMarksClosedWhenTransportClosed() {
        when(transport.isOpen()).thenReturn(false);
        OutgoingAgentFrame frame = session.populateOutgoing(OutgoingAgentFrame.builder().type("hello"));

        boolean sent = session.send(frame);

        assertThat(sent).isFalse();
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }

    @Test
    void sendReturnsFalseAndClosesOnIoException() throws Exception {
        doThrow(new IOException("boom")).when(transport).sendMessage(any(TextMessage.class));
        OutgoingAgentFrame frame = session.populateOutgoing(OutgoingAgentFrame.builder().type("hello"));

        boolean sent = session.send(frame);

        assertThat(sent).isFalse();
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }

    @Test
    void closeClosesOpenTransportAndMarksClosed() throws Exception {
        session.close(CloseStatus.NORMAL);

        verify(transport).close(CloseStatus.NORMAL);
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }

    @Test
    void closeStillMarksClosedWhenTransportThrows() throws Exception {
        doThrow(new IOException("ignored")).when(transport).close(any(CloseStatus.class));

        session.close(CloseStatus.SERVER_ERROR);

        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }

    @Test
    void closeSkipsTransportCloseWhenAlreadyClosed() throws Exception {
        when(transport.isOpen()).thenReturn(false);

        session.close(CloseStatus.NORMAL);

        verify(transport, never()).close(any(CloseStatus.class));
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }
}
