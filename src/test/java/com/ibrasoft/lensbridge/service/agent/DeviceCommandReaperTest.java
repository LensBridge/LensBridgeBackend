package com.ibrasoft.lensbridge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;
import com.ibrasoft.lensbridge.repository.sql.DeviceCommandRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceCommandReaperTest {

    private static final int DEADLINE_MS = 30_000;

    private DeviceCommandRepository commandRepo;
    private CommandDbFake db;
    private DeviceEventPublisher events;
    private DeviceCommandReaper reaper;

    @BeforeEach
    void setUp() {
        commandRepo = mock(DeviceCommandRepository.class);
        db = CommandDbFake.backing(commandRepo);
        events = mock(DeviceEventPublisher.class);
        CommandDispatcher dispatcher = new CommandDispatcher(
                commandRepo, mock(DeviceRepository.class), new AgentSessionRegistry(events),
                new ObjectMapper(), events);
        reaper = new DeviceCommandReaper(commandRepo, dispatcher, events);
    }

    @Test
    void expiresPendingCommandsPastTheirDeliveryWindow() {
        DeviceCommand stale = command(DeviceCommandStatus.PENDING);
        stale.setExpiresAt(Instant.now().minusSeconds(60));

        reaper.reap();

        DeviceCommand stored = db.row(stale.getId());
        assertEquals(DeviceCommandStatus.EXPIRED, stored.getStatus());
        assertNotNull(stored.getFinishedAt());
        verify(events).commandTerminated(any());
    }

    @ParameterizedTest
    @EnumSource(value = DeviceCommandStatus.class, names = {"DELIVERED", "ACKED", "RUNNING"})
    void timesOutInFlightCommandsThatNeverReportBack(DeviceCommandStatus stuckAt) {
        DeviceCommand stuck = command(stuckAt);
        stuck.setDeliveredAt(Instant.now().minusMillis(DEADLINE_MS).minusSeconds(60));

        reaper.reap();

        DeviceCommand stored = db.row(stuck.getId());
        assertEquals(DeviceCommandStatus.TIMEOUT, stored.getStatus());
        assertNotNull(stored.getFinishedAt());
        assertNotNull(stored.getErrorMessage());
        verify(events).commandTerminated(any());
    }

    @Test
    void leavesInFlightCommandsAloneWhileTheirDeadlineHolds() {
        DeviceCommand running = command(DeviceCommandStatus.RUNNING);
        running.setDeliveredAt(Instant.now());

        reaper.reap();

        assertEquals(DeviceCommandStatus.RUNNING, db.row(running.getId()).getStatus());
        verify(events, never()).commandTerminated(any());
    }

    /**
     * Regression: reap read the row, set TIMEOUT and saved the whole entity, so a result that
     * committed in between was overwritten by the timeout (the agent had in fact succeeded).
     * The reaper's copy says RUNNING; the result lands on the stored row first.
     */
    @Test
    void doesNotOverwriteAResultThatArrivedAfterItListedTheCommand() {
        DeviceCommand stuck = command(DeviceCommandStatus.RUNNING);
        stuck.setDeliveredAt(Instant.now().minusMillis(DEADLINE_MS).minusSeconds(60));
        when(commandRepo.findByStatusInAndDeliveredAtBefore(any(), any())).thenAnswer(inv -> {
            DeviceCommand listed = CommandDbFake.copy(db.row(stuck.getId()));
            // The agent's result commits right after the reaper's query.
            db.row(stuck.getId()).setStatus(DeviceCommandStatus.SUCCEEDED);
            db.row(stuck.getId()).setOutputJson("{\"ok\":true}");
            return List.of(listed);
        });

        reaper.reap();

        DeviceCommand stored = db.row(stuck.getId());
        assertEquals(DeviceCommandStatus.SUCCEEDED, stored.getStatus());
        assertEquals("{\"ok\":true}", stored.getOutputJson());
        assertNull(stored.getErrorMessage());
        verify(events, never()).commandTerminated(any());
    }

    @Test
    void doesNotExpireACommandThatWasDeliveredAfterItListedIt() {
        DeviceCommand stale = command(DeviceCommandStatus.PENDING);
        stale.setExpiresAt(Instant.now().minusSeconds(60));
        when(commandRepo.findByStatusAndExpiresAtBefore(any(), any())).thenAnswer(inv -> {
            DeviceCommand listed = CommandDbFake.copy(db.row(stale.getId()));
            db.row(stale.getId()).setStatus(DeviceCommandStatus.DELIVERED);
            return List.of(listed);
        });

        reaper.reap();

        assertEquals(DeviceCommandStatus.DELIVERED, db.row(stale.getId()).getStatus());
        verify(events, never()).commandTerminated(any());
    }

    @Test
    void doesNothingWhenThereIsNothingToReap() {
        reaper.reap();

        verify(commandRepo, never()).finish(any(), any(), any(), any(), any(), any());
        verify(events, never()).commandTerminated(any());
    }

    private DeviceCommand command(DeviceCommandStatus status) {
        return db.insert(DeviceCommand.builder()
                .id(UUID.randomUUID())
                .deviceId(UUID.randomUUID())
                .kind("chrome.reload")
                .payloadJson("{}")
                .issuedBy("admin")
                .deadlineMs(DEADLINE_MS)
                .status(status)
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }
}
