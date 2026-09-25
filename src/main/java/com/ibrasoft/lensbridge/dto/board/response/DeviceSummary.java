package com.ibrasoft.lensbridge.dto.board.response;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.BoardReport;
import com.ibrasoft.lensbridge.model.board.Device;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-facing summary of a Device. Excludes the public key bytes (operators don't need them).
 */
@Data
@Builder
public class DeviceSummary {

    private UUID id;
    private String displayName;
    private Audience audience;
    private Instant enrolledAt;
    private Instant lastHeartbeat;
    private Instant revokedAt;
    private String agentVersion;
    private String hardwareModel;
    private String lastSeenIp;
    /** The board's latest report of itself; null until an agent that sends one heartbeats. */
    private BoardReport board;
    private Instant boardReportAt;

    public static DeviceSummary of(Device d) {
        return DeviceSummary.builder()
                .id(d.getId())
                .displayName(d.getDisplayName())
                .audience(d.getAudience())
                .enrolledAt(d.getEnrolledAt())
                .lastHeartbeat(d.getLastHeartbeat())
                .revokedAt(d.getRevokedAt())
                .agentVersion(d.getAgentVersion())
                .hardwareModel(d.getHardwareModel())
                .lastSeenIp(d.getLastSeenIp())
                .board(d.getBoardReport())
                .boardReportAt(d.getBoardReportAt())
                .build();
    }
}
