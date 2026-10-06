package com.ibrasoft.lensbridge.service.agent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibrasoft.lensbridge.dto.board.agent.CommandAckFrame;
import com.ibrasoft.lensbridge.dto.board.agent.CommandProgressFrame;
import com.ibrasoft.lensbridge.dto.board.agent.CommandResultFrame;
import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.dto.board.response.CommandIssuedResponse;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommandDispatcherTest {

    private DeviceCommandRepository commandRepo;
    private CommandDbFake db;
    private DeviceRepository deviceRepo;
    private AgentSessionRegistry registry;
    private ObjectMapper mapper;
    private DeviceEventPublisher events;
    private CommandDispatcher dispatcher;

    private ch.qos.logback.classic.Logger dispatcherLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        commandRepo = mock(DeviceCommandRepository.class);
        db = CommandDbFake.backing(commandRepo);
        deviceRepo = mock(DeviceRepository.class);
        mapper = new ObjectMapper();
        events = mock(DeviceEventPublisher.class);
        registry = new AgentSessionRegistry(events);
        dispatcher = new CommandDispatcher(commandRepo, deviceRepo, registry, mapper, events);

        dispatcherLogger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(CommandDispatcher.class);
        logs = new ListAppender<>();
        logs.start();
        dispatcherLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        dispatcherLogger.detachAppender(logs);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void issue_persistsAsPending_whenDeviceOffline() {
        UUID deviceId = registerDevice();

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin@example.com",
                new IssueCommandRequest("chrome.reload", null, null, null));

        assertEquals(deviceId, resp.deviceId());
        assertEquals(DeviceCommandStatus.PENDING, resp.status());
        verify(events).commandIssued(any());
        verify(events, never()).commandDelivered(any());
    }

    @Test
    void issue_pushesToLiveSession_andMarksDelivered() throws Exception {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin",
                new IssueCommandRequest("chrome.reload", null, 15_000, null));

        verify(session.getTransport(), atLeastOnce()).sendMessage(any());
        verify(events).commandDelivered(any());
        assertEquals(DeviceCommandStatus.DELIVERED, db.row(resp.commandId()).getStatus());
        assertNotNull(db.row(resp.commandId()).getDeliveredAt());
    }

    @Test
    void issue_leavesPending_whenRegisteredSessionIsClosed() {
        UUID deviceId = registerDevice();
        closedSession(deviceId);

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin",
                new IssueCommandRequest("chrome.reload", null, 15_000, null));

        assertEquals(DeviceCommandStatus.PENDING, resp.status());
        verify(events, never()).commandDelivered(any());
    }

    @Test
    void issue_rejectsUnknownKind() {
        UUID deviceId = registerDevice();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                dispatcher.issue(deviceId, "admin", new IssueCommandRequest("not.a.thing", null, null, null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void issue_rejectsRevokedDevice() {
        UUID deviceId = UUID.randomUUID();
        Device d = device(deviceId);
        d.setRevokedAt(Instant.now().minusSeconds(60));
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(d));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                dispatcher.issue(deviceId, "admin", new IssueCommandRequest("chrome.reload", null, null, null)));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── ordering around the commit ────────────────────────────────────────

    /**
     * Regression: the frame and the STOMP event used to go out inside the transaction, before
     * the commit. An agent answering at once then looked the command up, found no row, and its
     * ack was dropped. Inside a transaction nothing may leave until the commit has happened.
     */
    @Test
    void issue_insideATransaction_deliversAndPublishesOnlyAfterCommit() throws Exception {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.issue(deviceId, "admin", new IssueCommandRequest("chrome.reload", null, null, null));

        verify(session.getTransport(), never()).sendMessage(any());
        verify(events, never()).commandIssued(any());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(session.getTransport()).sendMessage(any(TextMessage.class));
        verify(events).commandIssued(any());
        verify(events).commandDelivered(any());
    }

    @Test
    void issue_insideATransactionThatRollsBack_neverDeliversOrPublishes() throws Exception {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.issue(deviceId, "admin", new IssueCommandRequest("chrome.reload", null, null, null));
        // A rollback runs afterCompletion only; afterCommit is never called.
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(session.getTransport(), never()).sendMessage(any());
        verifyNoInteractions(events);
    }

    /**
     * The fastest possible agent: its ack is processed while the frame is still being written,
     * before the DELIVERED write. The ack must find the (already committed) row and win; the
     * late DELIVERED write must then leave the status alone.
     */
    @Test
    void anAckThatOutrunsTheDeliveredWriteIsNotLost() throws Exception {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        doAnswer(inv -> {
            UUID commandId = UUID.fromString(mapper.readTree(((TextMessage) inv.getArgument(0)).getPayload())
                    .get("commandId").asText());
            CommandAckFrame ack = new CommandAckFrame();
            ack.setCommandId(commandId);
            dispatcher.onAck(ack, session);
            return null;
        }).when(session.getTransport()).sendMessage(any());

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin",
                new IssueCommandRequest("chrome.reload", null, null, null));

        DeviceCommand stored = db.row(resp.commandId());
        assertEquals(DeviceCommandStatus.ACKED, stored.getStatus());
        assertNotNull(stored.getAckedAt());
        assertNotNull(stored.getDeliveredAt(), "the ack stamps delivery so the reaper deadline still works");
        verify(events).commandAcked(any());
        verify(events, never()).commandDelivered(any());
    }

    // ── result handling ───────────────────────────────────────────────────

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

        DeviceCommand stored = db.row(cmd.getId());
        assertEquals(DeviceCommandStatus.SUCCEEDED, stored.getStatus());
        assertNotNull(stored.getFinishedAt());
        assertEquals("{\"ok\":true}", stored.getOutputJson());
        verify(events).commandResult(any(), eq(frame));
    }

    @ParameterizedTest
    @CsvSource({"ok,SUCCEEDED", "error,FAILED", "timeout,TIMEOUT", "rejected,REJECTED"})
    void onResult_mapsEveryStatusTheAgentSends(String agentStatus, DeviceCommandStatus expected) {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.RUNNING);

        CommandResultFrame frame = new CommandResultFrame();
        frame.setCommandId(cmd.getId());
        frame.setStatus(agentStatus);
        frame.setErrorMessage("why");
        dispatcher.onResult(frame, session);

        assertEquals(expected, db.row(cmd.getId()).getStatus());
        assertEquals("why", db.row(cmd.getId()).getErrorMessage());
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

        assertEquals(DeviceCommandStatus.SUCCEEDED, db.row(cmd.getId()).getStatus());
        verify(events, never()).commandResult(any(), any());
    }

    /**
     * The reaper times the command out between the moment the result handler reads it (still
     * RUNNING) and the moment it writes. The conditional update must refuse, the reaper's
     * outcome must stand, and no result event may be published for the loser.
     */
    @Test
    void onResult_losingARaceWithTheReaperChangesNothingAndPublishesNothing() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.RUNNING);
        // The handler reads a RUNNING copy; the reaper then commits TIMEOUT underneath it.
        when(commandRepo.findById(cmd.getId())).thenAnswer(inv -> {
            DeviceCommand snapshot = CommandDbFake.copy(db.row(cmd.getId()));
            db.row(cmd.getId()).setStatus(DeviceCommandStatus.TIMEOUT);
            return Optional.of(snapshot);
        });

        CommandResultFrame frame = new CommandResultFrame();
        frame.setCommandId(cmd.getId());
        frame.setStatus("ok");
        dispatcher.onResult(frame, session);

        assertEquals(DeviceCommandStatus.TIMEOUT, db.row(cmd.getId()).getStatus());
        verify(events, never()).commandResult(any(), any());
    }

    // ── ack and progress ──────────────────────────────────────────────────

    @Test
    void onAck_movesDeliveredToAcked() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.DELIVERED);

        dispatcher.onAck(ack(cmd), session);

        assertEquals(DeviceCommandStatus.ACKED, db.row(cmd.getId()).getStatus());
        assertNotNull(db.row(cmd.getId()).getAckedAt());
        verify(events).commandAcked(any());
    }

    @Test
    void onAck_neverMovesRunningBackToAcked() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.RUNNING);

        dispatcher.onAck(ack(cmd), session);

        assertEquals(DeviceCommandStatus.RUNNING, db.row(cmd.getId()).getStatus());
        verify(events, never()).commandAcked(any());
    }

    @Test
    void onAck_repeatedAckPublishesOnce() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.DELIVERED);

        dispatcher.onAck(ack(cmd), session);
        dispatcher.onAck(ack(cmd), session);

        verify(events, times(1)).commandAcked(any());
    }

    /** A progress frame overtakes the ack: the later ack must not undo RUNNING. */
    @Test
    void onAck_afterProgressDoesNotRegress() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.DELIVERED);
        CommandProgressFrame progress = new CommandProgressFrame();
        progress.setCommandId(cmd.getId());
        dispatcher.onProgress(progress, session);

        dispatcher.onAck(ack(cmd), session);

        assertEquals(DeviceCommandStatus.RUNNING, db.row(cmd.getId()).getStatus());
    }

    @Test
    void onAck_ignoresMismatchedDevice() {
        UUID deviceA = registerDevice();
        UUID deviceB = UUID.randomUUID();
        AgentSession sessionB = liveSession(deviceB);
        DeviceCommand cmdForA = persistedCommand(deviceA, DeviceCommandStatus.DELIVERED);

        dispatcher.onAck(ack(cmdForA), sessionB);

        assertEquals(DeviceCommandStatus.DELIVERED, db.row(cmdForA.getId()).getStatus());
        verify(events, never()).commandAcked(any());
    }

    @Test
    void onProgress_marksRunningOnceAndPublishesEveryFrame() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.ACKED);
        CommandProgressFrame progress = new CommandProgressFrame();
        progress.setCommandId(cmd.getId());

        dispatcher.onProgress(progress, session);
        Instant startedAt = db.row(cmd.getId()).getStartedAt();
        dispatcher.onProgress(progress, session);

        assertEquals(DeviceCommandStatus.RUNNING, db.row(cmd.getId()).getStatus());
        assertEquals(startedAt, db.row(cmd.getId()).getStartedAt());
        verify(events, times(2)).commandProgress(any(), eq(progress));
        verify(commandRepo, times(1)).markRunning(any(), any());
    }

    @Test
    void onProgress_ignoresTerminalCommands() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.TIMEOUT);
        CommandProgressFrame progress = new CommandProgressFrame();
        progress.setCommandId(cmd.getId());

        dispatcher.onProgress(progress, session);

        assertEquals(DeviceCommandStatus.TIMEOUT, db.row(cmd.getId()).getStatus());
        verify(events, never()).commandProgress(any(), any());
    }

    // ── frames about commands we do not have ──────────────────────────────

    @Test
    void frameForAnUnknownCommand_isLoggedAtWarnNotSilentlyDropped() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);
        UUID missing = UUID.randomUUID();

        CommandAckFrame ack = new CommandAckFrame();
        ack.setCommandId(missing);
        dispatcher.onAck(ack, session);
        CommandProgressFrame progress = new CommandProgressFrame();
        progress.setCommandId(missing);
        dispatcher.onProgress(progress, session);
        CommandResultFrame result = new CommandResultFrame();
        result.setCommandId(missing);
        result.setStatus("ok");
        dispatcher.onResult(result, session);

        List<ILoggingEvent> warnings = logs.list.stream()
                .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(missing.toString()))
                .toList();
        assertThat(warnings).hasSize(3);
        assertThat(warnings).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("command_ack"))
                .anyMatch(m -> m.contains("command_progress"))
                .anyMatch(m -> m.contains("command_result"));
        verifyNoInteractions(events);
    }

    @Test
    void frameWithoutACommandId_isLoggedAtWarn() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        dispatcher.onAck(new CommandAckFrame(), session);

        assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.WARN
                && e.getFormattedMessage().contains("no commandId"));
    }

    // ── flush and expiry ──────────────────────────────────────────────────

    @Test
    void flushPending_deliversAllPendingCommands() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        DeviceCommand c1 = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        DeviceCommand c2 = persistedCommand(deviceId, DeviceCommandStatus.PENDING);

        dispatcher.flushPending(deviceId, session);

        assertEquals(DeviceCommandStatus.DELIVERED, db.row(c1.getId()).getStatus());
        assertEquals(DeviceCommandStatus.DELIVERED, db.row(c2.getId()).getStatus());
        verify(events, times(2)).commandDelivered(any());
    }

    @Test
    void flushPending_expiresStaleCommandsInsteadOfDelivering() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        DeviceCommand fresh = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        DeviceCommand stale = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        stale.setKind("system.reboot");
        stale.setExpiresAt(Instant.now().minusSeconds(1));

        dispatcher.flushPending(deviceId, session);

        DeviceCommand expired = db.row(stale.getId());
        assertEquals(DeviceCommandStatus.EXPIRED, expired.getStatus());
        assertNotNull(expired.getFinishedAt());
        assertNotNull(expired.getErrorMessage());
        assertEquals(DeviceCommandStatus.DELIVERED, db.row(fresh.getId()).getStatus());
        verify(events, times(1)).commandDelivered(any());
        verify(events, times(1)).commandTerminated(any());
    }

    @Test
    void flushPending_treatsMissingExpiryAsNonExpiring() {
        UUID deviceId = registerDevice();
        AgentSession session = liveSession(deviceId);

        // Rows written before expiry support carry a null expiresAt.
        DeviceCommand legacy = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        legacy.setExpiresAt(null);

        dispatcher.flushPending(deviceId, session);

        assertEquals(DeviceCommandStatus.DELIVERED, db.row(legacy.getId()).getStatus());
    }

    @Test
    void expire_doesNothingWhenTheCommandWasDeliveredInTheMeantime() {
        UUID deviceId = registerDevice();
        DeviceCommand cmd = persistedCommand(deviceId, DeviceCommandStatus.PENDING);
        cmd.setExpiresAt(Instant.now().minusSeconds(1));
        DeviceCommand staleCopy = CommandDbFake.copy(cmd);
        cmd.setStatus(DeviceCommandStatus.DELIVERED); // the stored row moved on

        boolean expired = dispatcher.expire(staleCopy, Instant.now());

        assertFalse(expired);
        assertEquals(DeviceCommandStatus.DELIVERED, db.row(cmd.getId()).getStatus());
        verify(events, never()).commandTerminated(any());
    }

    @Test
    void issue_stampsExpiryFromDefaultTtl() {
        UUID deviceId = registerDevice();

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin",
                new IssueCommandRequest("chrome.reload", null, null, null));

        assertNotNull(resp.expiresAt());
        assertTrue(resp.expiresAt().isAfter(resp.issuedAt()));
    }

    @Test
    void issue_honoursExplicitTtl() {
        UUID deviceId = registerDevice();

        CommandIssuedResponse resp = dispatcher.issue(deviceId, "admin",
                new IssueCommandRequest("system.reboot", null, null, 30));

        assertEquals(30, resp.expiresAt().getEpochSecond() - resp.issuedAt().getEpochSecond());
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

    /** Inserts a row into the fake database and returns the stored instance. */
    private DeviceCommand persistedCommand(UUID deviceId, DeviceCommandStatus status) {
        return db.insert(DeviceCommand.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId)
                .kind("chrome.reload")
                .payloadJson("{}")
                .issuedBy("admin")
                .deadlineMs(30_000)
                .status(status)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private static CommandAckFrame ack(DeviceCommand cmd) {
        CommandAckFrame ack = new CommandAckFrame();
        ack.setCommandId(cmd.getId());
        return ack;
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
