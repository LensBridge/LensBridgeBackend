package com.ibrasoft.minbar.signage.dto.response;

import com.ibrasoft.minbar.signage.model.Audience;
import com.ibrasoft.minbar.signage.model.Device;
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
                .build();
    }
}
