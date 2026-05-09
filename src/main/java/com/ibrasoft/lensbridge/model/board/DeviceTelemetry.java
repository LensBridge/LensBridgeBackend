package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only telemetry sample emitted by an agent over its WebSocket session.
 * Retention is bounded by a periodic prune (default 30 days).
 */
@Entity
@Table(name = "device_telemetry", indexes = {
        @Index(name = "idx_device_telemetry_device_recorded",
               columnList = "device_id, recorded_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    private Long uptimeSec;
    private Double cpuTempC;
    private String throttleFlags;

    private Integer memUsedMb;
    private Integer memTotalMb;
    private Integer diskUsedPct;

    private Boolean kioskAlive;

    /** Comma-separated list of non-loopback IPv4 addresses. */
    private String ipv4;

    private String wifiSsid;

    /** UUID of the FrameDefinition currently on screen, if known. */
    private UUID displayedFrameId;
}
