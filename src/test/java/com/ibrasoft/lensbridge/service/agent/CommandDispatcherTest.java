package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibrasoft.lensbridge.dto.agent.CommandAckFrame;
import com.ibrasoft.lensbridge.dto.agent.CommandResultFrame;
import com.ibrasoft.lensbridge.dto.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommandDispatcherTest {

    private DeviceCommandRepository commandRepo;
    private DeviceRepository deviceRepo;
    private AgentSessionRegistry registry;
    private ObjectMapper mapper;
    private DeviceEventPublisher events;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        commandRepo = mock(DeviceCommandRepository.class);
        deviceRepo = mock(DeviceRepository.class);
        registry = new AgentSessionRegistry();
        mapper = new ObjectMapper();
        events = mock(DeviceEventPublisher.class);
        dispatcher = new CommandDispatcher(commandRepo, deviceRepo, registry, mapper, events);

        when(commandRepo.save(any())).thenAnswer(inv -> {
            DeviceCommand c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            if (c.getIssuedAt() == null) c.setIssuedAt(Instant.now());
            return c;
        });
    }

    @Test
    void issue_persistsAsPending_whenDeviceOffline() {
        UUID deviceId = registerDevice();

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin@example.com",
                new IssueCommandRequest("chrome.reload", null, null));

        assertEquals(deviceId, resp.deviceId());
        assertEquals(DeviceCommandStatus.PENDING, resp.status());
        verify(events).commandIssued(any());
        verify(events, never()).commandDelivered(any());
    }

    @Test
    void issue_pushesToLiveSession_andMarksDelivered() throws Exception {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        dispatcher.issue(deviceId, "admin", new IssueCommandRequest("chrome.reload", null, 15_000));

        verify(session.getTransport(), atLeastOnce()).sendMessage(any());
        verify(events).commandDelivered(any());
    }

    @Test
    void issue_leavesPending_whenRegisteredSessionIsClosed() {
        UUID deviceId = registerDevice();
        AgentSession session = closedSession(deviceId);

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin",
                new IssueCommandRequest("chrome.reload", null, 15_000));

        assertEquals(DeviceCommandStatus.PENDING, resp.status());
        verify(events, never()).commandDelivered(any());
    }

    @Test
    void issue_rejectsUnknownKind() {
        UUID deviceId = registerDevice();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                dispatcher.issue(deviceId, "admin", new IssueCommandRequest("not.a.thing", null, null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void issue_rejectsRevokedDevice() {
        UUID deviceId = UUID.randomUUID();
        Device d = device(deviceId);
        d.setRevokedAt(Instant.now().minusSeconds(60));
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(d));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                dispatcher.issue(deviceId, "admin", new IssueCommandRequest("chrome.reload", null, null)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void onResult_movesToTerminalState() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.RUNNING);

        CommandResultFrame frame = new CommandResultFrame();
        frame.setCommandId(cmd.getId());
        frame.setStatus("ok");
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("ok", true);
        frame.setOutput(out);
        frame.setDurationMs(123L);

        dispatcher.onResult(frame, session);

        assertEquals(DeviceCommandStatus.SUCCEEDED, cmd.getStatus());
        assertNotNull(cmd.getFinishedAt());
        verify(events).commandResult(eq(cmd), eq(frame));
    }

    @Test
    void onResult_isIdempotentForTerminalStates() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.SUCCEEDED);
        cmd.setFinishedAt(Instant.now());

        CommandResultFrame frame = new CommandResultFrame();
        frame.setCommandId(cmd.getId());
        frame.setStatus("error");
        dispatcher.onResult(frame, session);

        assertEquals(DeviceCommandStatus.SUCCEEDED, cmd.getStatus());
        verify(events, never()).commandResult(any(), any());
    }

    @Test
    void onAck_ignoresMismatchedDevice() {
        UUID deviceA = registerDevice();
        UUID deviceB = UUID.randomUUID();
        AgentSession sessionB = liveSession(deviceB);
        DeviceCommand cmdForA = persistedCommand(deviceA, DeviceCommandStatus.DELIVERED);

        CommandAckFrame ack = new CommandAckFrame();
        ack.setCommandId(cmdForA.getId());
        dispatcher.onAck(ack, sessionB);

        assertEquals(DeviceCommandStatus.DELIVERED, cmdForA.getStatus());
        verify(events, never()).commandAcked(any());
    }

    @Test
    void flushPending_deliversAllPendingCommands() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        DeviceCommand c1 = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        DeviceCommand c2 = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        when(commandRepo.findByDeviceIdAndStatusOrderByIssuedAtAsc(deviceId, DeviceCommandStatus.PENDING))
                .thenReturn(List.of(c1, c2));

        dispatcher.flushPending(deviceId, session);

        assertEquals(DeviceCommandStatus.DELIVERED, c1.getStatus());
        assertEquals(DeviceCommandStatus.DELIVERED, c2.getStatus());
        verify(events, times(2)).commandDelivered(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private UUID registerDevice() {
        UUID id = UUID.randomUUID();
        when(deviceRepo.findById(id)).thenReturn(Optional.of(device(id)));
        return id;
    }

    private Device device(UUID id) {
        return Device.builder()
                .id(id)
                .displayName("test")
                .audience(Audience.BOTH)
                .publicKey(new byte[32])
                .build();
    }

    private DeviceCommand persistedCommand(UUID deviceId, DeviceCommandStatus status) {
        DeviceCommand cmd = DeviceCommand.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .kind("chrome.reload")
                .payloadJson("{}")
                .issuedBy("admin")
                .deadlineMs(30_000)
                .status(status)
                .issuedAt(Instant.now())
                .build();
        when(commandRepo.findById(cmd.getId())).thenReturn(Optional.of(cmd));
        return cmd;
    }

    private AgentSession liveSession(UUID deviceId) {
        WebSocketSession transport = mock(WebSocketSession.class);
        when(transport.isOpen()).thenReturn(true);
        AgentSession session = new AgentSession(transport, "challenge-base64", mapper);
        session.markAuthenticated(deviceId);
        registry.register(deviceId, session);
        return session;
    }

    private AgentSession closedSession(UUID deviceId) {
        WebSocketSession transport = mock(WebSocketSession.class);
        when(transport.isOpen()).thenReturn(false);
        AgentSession session = new AgentSession(transport, "challenge-base64", mapper);
        session.markAuthenticated(deviceId);
        registry.register(deviceId, session);
        return session;
    }

}
