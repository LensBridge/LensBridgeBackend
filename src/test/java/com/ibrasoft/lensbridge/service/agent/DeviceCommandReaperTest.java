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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceCommandReaperTest {

    private static final int DEADLINE_MS = 30_000;

    private DeviceCommandRepository commandRepo;
    private DeviceEventPublisher events;
    private DeviceCommandReaper reaper;

    @BeforeEach
    void setUp() {
        commandRepo = mock(DeviceCommandRepository.class);
        events = mock(DeviceEventPublisher.class);
        CommandDispatcher dispatcher = new CommandDispatcher(
                commandRepo, mock(DeviceRepository.class), new AgentSessionRegistry(),
                new ObjectMapper(), events);
        reaper = new DeviceCommandReaper(commandRepo, dispatcher, events);

        when(commandRepo.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(List.of());
        when(commandRepo.findByStatusInAndDeliveredAtBefore(any(), any())).thenReturn(List.of());
        when(commandRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void expiresPendingCommandsPastTheirDeliveryWindow() {
        DeviceCommand stale = command(DeviceCommandStatus.PENDING);
        stale.setExpiresAt(Instant.now().minusSeconds(60));
        when(commandRepo.findByStatusAndExpiresAtBefore(eq(DeviceCommandStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        reaper.reap();

        assertEquals(DeviceCommandStatus.EXPIRED, stale.getStatus());
        assertNotNull(stale.getFinishedAt());
        verify(events).commandTerminated(stale);
    }

    @ParameterizedTest
    @EnumSource(value = DeviceCommandStatus.class, names = {"DELIVERED", "ACKED", "RUNNING"})
    void timesOutInFlightCommandsThatNeverReportBack(DeviceCommandStatus stuckAt) {
        DeviceCommand stuck = command(stuckAt);
        stuck.setDeliveredAt(Instant.now().minusMillis(DEADLINE_MS).minusSeconds(60));
        when(commandRepo.findByStatusInAndDeliveredAtBefore(anyCollection(), any()))
                .thenReturn(List.of(stuck));

        reaper.reap();

        assertEquals(DeviceCommandStatus.TIMEOUT, stuck.getStatus());
        assertNotNull(stuck.getFinishedAt());
        verify(events).commandTerminated(stuck);
    }

    @Test
    void leavesInFlightCommandsAloneWhileTheirDeadlineHolds() {
        DeviceCommand running = command(DeviceCommandStatus.RUNNING);
        running.setDeliveredAt(Instant.now());
        when(commandRepo.findByStatusInAndDeliveredAtBefore(anyCollection(), any()))
                .thenReturn(List.of(running));

        reaper.reap();

        assertEquals(DeviceCommandStatus.RUNNING, running.getStatus());
        verify(events, never()).commandTerminated(any());
    }

    @Test
    void doesNothingWhenThereIsNothingToReap() {
        reaper.reap();

        verify(commandRepo, never()).save(any());
        verify(events, never()).commandTerminated(any());
    }

    private static DeviceCommand command(DeviceCommandStatus status) {
        return DeviceCommand.builder()
                .id(UUID.randomUUID())
                .deviceId(UUID.randomUUID())
                .kind("chrome.reload")
                .payloadJson("{}")
                .issuedBy("admin")
                .deadlineMs(DEADLINE_MS)
                .status(status)
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
