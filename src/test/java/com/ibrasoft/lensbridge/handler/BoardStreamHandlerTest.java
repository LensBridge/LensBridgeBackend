package com.ibrasoft.lensbridge.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardStreamHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private DeviceRepository deviceRepository;
    private BoardStreamHandler handler;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        when(deviceRepository.findById(any())).thenReturn(Optional.empty());
        handler = new BoardStreamHandler(mapper, deviceRepository);
    }

    @Test
    void contentPushReachesEveryConnectedBoard() throws Exception {
        Session a = connect(enrolled());
        Session b = connect(enrolled());

        handler.contentChanged("posters");

        assertThat(a.sent).hasSize(1);
        assertThat(b.sent).hasSize(1);
    }

    @Test
    void configPushReachesOnlyTheTargetBoard() throws Exception {
        UUID target = enrolled();
        Session mine = connect(target);
        Session theirs = connect(enrolled());

        handler.configChanged(target);

        assertThat(mine.sent).hasSize(1);
        assertThat(theirs.sent).isEmpty();
    }

    @Test
    void messageIsJsonCarryingTypeReasonAndTarget() throws Exception {
        UUID target = enrolled();
        Session s = connect(target);

        handler.configChanged(target);

        JsonNode body = mapper.readTree(s.sent.get(0));
        assertThat(body.get("type").asText()).isEqualTo("refresh");
        assertThat(body.get("reason").asText()).isEqualTo("config");
        assertThat(body.get("deviceId").asText()).isEqualTo(target.toString());
        assertThat(body.hasNonNull("at")).isTrue();
    }

    /** The deployed kiosk substring-matches rather than parsing, so JSON must still hit it. */
    @Test
    void messageStillMatchesTheLegacyRefreshSubstring() throws Exception {
        Session s = connect(enrolled());

        handler.refreshAll();

        assertThat(s.sent.get(0).toUpperCase()).contains("REFRESH");
    }

    // ── enrollment is a precondition, not a state ─────────────────────────

    @Test
    void connectionWithoutADeviceIdIsRejected() throws Exception {
        Session s = connectWithQuery(null);

        assertThat(s.closedWith).isNotNull();
        assertThat(s.closedWith.getCode()).isEqualTo(4001);

        handler.contentChanged("events");
        assertThat(s.sent).isEmpty();
    }

    @Test
    void malformedDeviceIdIsRejected() throws Exception {
        Session s = connectWithQuery("deviceId=not-a-uuid");

        assertThat(s.closedWith.getCode()).isEqualTo(4001);
    }

    @Test
    void unknownDeviceIsRejected() throws Exception {
        // deviceRepository returns empty for anything not explicitly enrolled.
        Session s = connectWithQuery("deviceId=" + UUID.randomUUID());

        assertThat(s.closedWith.getCode()).isEqualTo(4002);

        handler.contentChanged("events");
        assertThat(s.sent).isEmpty();
    }

    @Test
    void revokedDeviceIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        Device revoked = Device.builder().id(id).displayName("d").build();
        revoked.setRevokedAt(Instant.now().minusSeconds(60));
        when(deviceRepository.findById(id)).thenReturn(Optional.of(revoked));

        Session s = connect(id);

        assertThat(s.closedWith.getCode()).isEqualTo(4003);
    }

    // ── session lifecycle ─────────────────────────────────────────────────

    @Test
    void reconnectSupersedesThePriorSessionForTheSameDevice() throws Exception {
        UUID device = enrolled();
        Session first = connect(device);
        Session second = connect(device);

        assertThat(first.closedWith).isNotNull();

        handler.configChanged(device);
        assertThat(second.sent).hasSize(1);
        assertThat(first.sent).isEmpty();
    }

    /**
     * A superseded session's close callback arrives after its replacement is already
     * registered. It must not evict the newcomer.
     */
    @Test
    void lateCloseOfASupersededSessionLeavesTheReplacementRegistered() throws Exception {
        UUID device = enrolled();
        Session first = connect(device);
        Session second = connect(device);

        handler.afterConnectionClosed(first.ws, CloseStatus.NORMAL);

        handler.configChanged(device);
        assertThat(second.sent).hasSize(1);
    }

    @Test
    void disconnectStopsDelivery() throws Exception {
        Session s = connect(enrolled());
        handler.afterConnectionClosed(s.ws, CloseStatus.NORMAL);

        handler.contentChanged("events");

        assertThat(s.sent).isEmpty();
    }

    @Test
    void closedSessionsAreDroppedAndNeverWrittenTo() throws Exception {
        Session s = connect(enrolled());
        s.open = false;

        handler.contentChanged("events");

        assertThat(s.sent).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Registers a device the repository will vouch for, and returns its id. */
    private UUID enrolled() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.findById(id))
                .thenReturn(Optional.of(Device.builder().id(id).displayName("board").build()));
        return id;
    }

    private Session connect(UUID deviceId) throws Exception {
        return connectWithQuery("deviceId=" + deviceId);
    }

    private Session connectWithQuery(String query) throws Exception {
        Session session = new Session(query);
        handler.afterConnectionEstablished(session.ws);
        return session;
    }

    /** Minimal stand-in that records what the handler writes and how it closes. */
    private static class Session {
        final WebSocketSession ws = mock(WebSocketSession.class);
        final List<String> sent = new ArrayList<>();
        boolean open = true;
        CloseStatus closedWith;

        Session(String query) throws Exception {
            when(ws.getId()).thenReturn(UUID.randomUUID().toString());
            when(ws.getUri()).thenReturn(URI.create(
                    "ws://board.local/api/refresh-musallahboard" + (query == null ? "" : "?" + query)));
            when(ws.isOpen()).thenAnswer(inv -> open);
            when(ws.getAttributes()).thenReturn(new HashMap<>());
            doAnswer(inv -> {
                sent.add(((TextMessage) inv.getArgument(0)).getPayload());
                return null;
            }).when(ws).sendMessage(any());
            doAnswer(inv -> {
                closedWith = inv.getArgument(0);
                open = false;
                return null;
            }).when(ws).close(any(CloseStatus.class));
        }
    }
}
