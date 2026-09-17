package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.dto.board.agent.HeartbeatFrame;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.DeviceTelemetry;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceTelemetryRepository;
import com.ibrasoft.lensbridge.service.agent.events.DeviceEventPublisher;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists incoming heartbeat frames: bumps {@link Device#getLastHeartbeat()} and
 * appends a {@link DeviceTelemetry} row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private final DeviceRepository deviceRepository;
    private final DeviceTelemetryRepository telemetryRepository;
    private final DeviceEventPublisher events;

    @Transactional
    public void record(UUID deviceId, HeartbeatFrame frame, String remoteIp) {
        Instant now = Instant.now();

        HeartbeatFrame.Telemetry t = frame.getTelemetry();

        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLastHeartbeat(now);
            if (remoteIp != null && !remoteIp.isBlank()) {
                device.setLastSeenIp(remoteIp);
            }
            // Enrollment set this once; a binary push changes it without re-enrolling.
            String reported = t == null ? null : t.getAgentVersion();
            if (reported != null && !reported.isBlank() && !reported.equals(device.getAgentVersion())) {
                log.info("Device {} agent version {} → {}", deviceId, device.getAgentVersion(), reported);
                device.setAgentVersion(reported);
            }
            deviceRepository.save(device);
        });

        if (t == null) return;

        DeviceTelemetry row = DeviceTelemetry.builder()
                .deviceId(deviceId)
                .recordedAt(now)
                .uptimeSec(t.getUptimeSec())
                .cpuTempC(t.getCpuTempC())
                .throttleFlags(t.getThrottleFlags())
                .memUsedMb(t.getMemUsedMb())
                .memTotalMb(t.getMemTotalMb())
                .diskUsedPct(t.getDiskUsedPct())
                .kioskAlive(t.getKioskAlive())
                .ipv4(t.getIpv4() == null ? null : String.join(",", t.getIpv4()))
                .wifiSsid(t.getWifiSsid())
                .displayedFrameKey(truncate(t.getDisplayedFrameKey(), DeviceTelemetry.MAX_FRAME_KEY_LENGTH))
                .build();
        telemetryRepository.save(row);

        events.heartbeat(deviceId, frame);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
