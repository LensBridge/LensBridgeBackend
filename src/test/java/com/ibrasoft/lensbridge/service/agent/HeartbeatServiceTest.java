package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.dto.board.agent.HeartbeatFrame;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.DeviceTelemetry;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceTelemetryRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceTelemetryRepository telemetryRepository;
    @Mock
    private DeviceEventPublisher events;

    @InjectMocks
    private HeartbeatService service;

    private HeartbeatFrame frameWithTelemetry() {
        HeartbeatFrame frame = new HeartbeatFrame();
        HeartbeatFrame.Telemetry t = new HeartbeatFrame.Telemetry();
        t.setUptimeSec(1234L);
        t.setCpuTempC(55.5);
        t.setThrottleFlags("0x0");
        t.setMemUsedMb(512);
        t.setMemTotalMb(2048);
        t.setDiskUsedPct(40);
        t.setKioskAlive(true);
        t.setIpv4(List.of("10.0.0.1", "192.168.1.2"));
        t.setWifiSsid("MSA-WIFI");
        t.setDisplayedFrameKey("poster-3");
        frame.setTelemetry(t);
        return frame;
    }

    @Test
    void recordUpdatesLastHeartbeatAndIpForKnownDevice() {
        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder().id(deviceId).displayName("d").build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        Instant before = Instant.now();
        service.record(deviceId, frameWithTelemetry(), "203.0.113.9");

        assertThat(device.getLastHeartbeat()).isNotNull();
        assertThat(device.getLastHeartbeat()).isAfterOrEqualTo(before);
        assertThat(device.getLastSeenIp()).isEqualTo("203.0.113.9");
        verify(deviceRepository).save(device);
    }

    @Test
    void recordDoesNotOverwriteIpWhenRemoteIpBlank() {
        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder().id(deviceId).displayName("d").lastSeenIp("1.1.1.1").build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        service.record(deviceId, frameWithTelemetry(), "   ");

        assertThat(device.getLastSeenIp()).isEqualTo("1.1.1.1");
    }

    @Test
    void recordToleratesUnknownDeviceButStillPersistsTelemetry() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        service.record(deviceId, frameWithTelemetry(), "ip");

        verify(deviceRepository, never()).save(any());
        verify(telemetryRepository).save(any(DeviceTelemetry.class));
        verify(events).heartbeat(eq(deviceId), any());
    }

    @Test
    void recordPersistsTelemetryRowWithMappedFields() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());
        HeartbeatFrame frame = frameWithTelemetry();

        service.record(deviceId, frame, "ip");

        ArgumentCaptor<DeviceTelemetry> captor = ArgumentCaptor.forClass(DeviceTelemetry.class);
        verify(telemetryRepository).save(captor.capture());
        DeviceTelemetry row = captor.getValue();
        assertThat(row.getDeviceId()).isEqualTo(deviceId);
        assertThat(row.getRecordedAt()).isNotNull();
        assertThat(row.getUptimeSec()).isEqualTo(1234L);
        assertThat(row.getCpuTempC()).isEqualTo(55.5);
        assertThat(row.getIpv4()).isEqualTo("10.0.0.1,192.168.1.2");
        assertThat(row.getWifiSsid()).isEqualTo("MSA-WIFI");
    }

    @Test
    void recordSkipsTelemetryAndEventWhenTelemetryNull() {
        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder().id(deviceId).displayName("d").build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        HeartbeatFrame frame = new HeartbeatFrame();

        service.record(deviceId, frame, "ip");

        verify(deviceRepository).save(device);
        verifyNoInteractions(telemetryRepository);
        verifyNoInteractions(events);
    }

    @Test
    void recordPublishesHeartbeatEventAfterPersist() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());
        HeartbeatFrame frame = frameWithTelemetry();

        service.record(deviceId, frame, "ip");

        verify(events).heartbeat(deviceId, frame);
    }

    /** The board's report, as the agent sends it, parsed and stored as the device's state. */
    @Test
    void recordStoresTheBoardReport() throws Exception {
        String json = """
                {"type":"heartbeat","telemetry":{"agentVersion":"0.3.0","board":{
                  "appVersion":"2.2.0",
                  "content":{"firstDay":"2026-09-25","lastDay":"2026-10-01","createdAt":"2026-09-25T12:00:00Z",
                             "source":"usb","daysRemaining":6,"staleDays":0},
                  "updates":{"available":[{"type":"app","version":"2.3.0","description":"board app 2.3.0"}],
                             "installTime":"23:00","installAt":"2026-09-25T23:00:00-04:00","installing":false,
                             "autoUpdate":true,"lastCheckAt":"2026-09-25T18:00:00Z","lastCheckError":"no route"},
                  "servicePort":true,"usbImport":true,
                  "lastAgentUpdate":{"from":"0.2.1","to":"0.3.0","at":"2026-09-24T23:01:00Z","status":"ok","message":"m"},
                  "clock":{"source":"unverified","trusted":false},
                  "someFutureField":{"x":1}}}}
                """;
        HeartbeatFrame frame = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, HeartbeatFrame.class);
        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder().id(deviceId).displayName("d").build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        service.record(deviceId, frame, "ip");

        var board = device.getBoardReport();
        assertThat(board).isNotNull();
        assertThat(board.appVersion()).isEqualTo("2.2.0");
        assertThat(board.content().daysRemaining()).isEqualTo(6);
        assertThat(board.updates().available()).extracting(u -> u.version()).containsExactly("2.3.0");
        assertThat(board.lastAgentUpdate().status()).isEqualTo("ok");
        assertThat(board.clock().trusted()).isFalse();
        assertThat(board.updates().lastCheckError()).isEqualTo("no route");
        assertThat(board.servicePort()).isTrue();
        assertThat(device.getBoardReportAt()).isNotNull();

        // What is stored reads back the same.
        var converter = new com.ibrasoft.lensbridge.model.board.BoardReportConverter();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(board))).isEqualTo(board);
    }

    @Test
    void recordKeepsTheLastReportWhenAHeartbeatHasNone() {
        UUID deviceId = UUID.randomUUID();
        var report = new com.ibrasoft.lensbridge.model.board.BoardReport("2.2.0", null, null, null, null, null, false, false, null);
        Device device = Device.builder().id(deviceId).displayName("d").boardReport(report).build();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        service.record(deviceId, frameWithTelemetry(), "ip");

        assertThat(device.getBoardReport()).isEqualTo(report);
    }
}
