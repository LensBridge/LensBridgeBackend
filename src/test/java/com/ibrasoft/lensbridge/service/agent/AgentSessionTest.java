package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.dto.board.agent.OutgoingAgentFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentSessionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private WebSocketSession transport;
    private AgentSession session;

    @BeforeEach
    void setUp() {
        transport = mock(WebSocketSession.class);
        lenient().when(transport.isOpen()).thenReturn(true);
        session = new AgentSession(transport, "the-challenge", mapper);
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

    private List<JsonNode> written() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(transport, atLeast(0)).sendMessage(captor.capture());
        List<JsonNode> frames = new ArrayList<>();
        for (TextMessage m : captor.getAllValues()) frames.add(mapper.readTree(m.getPayload()));
        return frames;
    }

    @Test
    void sendAllocatesMonotonicSeqAndStampsSessionId() throws Exception {
        session.send(OutgoingAgentFrame.builder().type("hello"));
        session.send(OutgoingAgentFrame.builder().type("command"));

        List<JsonNode> frames = written();
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).get("seq").asLong()).isEqualTo(1L);
        assertThat(frames.get(1).get("seq").asLong()).isEqualTo(2L);
        assertThat(frames.get(0).get("sessionId").asText()).isEqualTo(session.getSessionId().toString());
        assertThat(frames.get(1).get("sessionId").asText()).isEqualTo(session.getSessionId().toString());
    }

    /**
     * The seq must be allocated inside the send lock: if two threads number their frames and
     * then race to write, the agent sees them out of order. Many threads, then check that what
     * reached the transport is exactly 1..N in write order.
     */
    @Test
    void concurrentSendsReachTheTransportInSeqOrder() throws Exception {
        List<Long> seqs = Collections.synchronizedList(new ArrayList<>());
        doAnswer(inv -> {
            seqs.add(mapper.readTree(((TextMessage) inv.getArgument(0)).getPayload()).get("seq").asLong());
            return null;
        }).when(transport).sendMessage(any(TextMessage.class));

        int threads = 8;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            done.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < perThread; i++) session.send(OutgoingAgentFrame.builder().type("command"));
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : done) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(seqs).hasSize(threads * perThread);
        for (int i = 0; i < seqs.size(); i++) {
            assertThat(seqs.get(i)).isEqualTo(i + 1L);
        }
    }

    @Test
    void sendWritesMessageWhenTransportOpen() throws Exception {
        boolean sent = session.send(OutgoingAgentFrame.builder().type("hello"));

        assertThat(sent).isTrue();
        verify(transport).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendReturnsFalseAndMarksClosedWhenTransportClosed() throws Exception {
        when(transport.isOpen()).thenReturn(false);

        boolean sent = session.send(OutgoingAgentFrame.builder().type("hello"));

        assertThat(sent).isFalse();
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
        verify(transport, never()).sendMessage(any());
    }

    @Test
    void aFrameThatCannotBeWrittenDoesNotConsumeASeq() throws Exception {
        when(transport.isOpen()).thenReturn(false);
        session.send(OutgoingAgentFrame.builder().type("command"));
        when(transport.isOpen()).thenReturn(true);

        session.send(OutgoingAgentFrame.builder().type("command"));

        assertThat(written().get(0).get("seq").asLong()).isEqualTo(1L);
    }

    @Test
    void sendReturnsFalseAndClosesOnIoException() throws Exception {
        doThrow(new IOException("boom")).when(transport).sendMessage(any(TextMessage.class));

        boolean sent = session.send(OutgoingAgentFrame.builder().type("hello"));

        assertThat(sent).isFalse();
        assertThat(session.getPhase()).isEqualTo(AgentSession.Phase.CLOSED);
    }

    @Test
    void touchAdvancesLastInboundTime() throws Exception {
        long before = session.getLastInboundAtMs();
        assertThat(before).isEqualTo(session.getConnectedAtMs());

        Thread.sleep(5);
        session.touch();

        assertThat(session.getLastInboundAtMs()).isGreaterThan(before);
        assertThat(session.getConnectedAtMs()).isEqualTo(before);
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
