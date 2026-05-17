package com.ibrasoft.lensbridge.dto.board.agent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class HeartbeatFrame extends IncomingAgentFrame {

    private Telemetry telemetry;

    @Getter
    @Setter
    public static class Telemetry {
        private Long uptimeSec;
        private Double cpuTempC;
        private String throttleFlags;
        private Integer memUsedMb;
        private Integer memTotalMb;
        private Integer diskUsedPct;
        private Boolean kioskAlive;
        private List<String> ipv4;
        private String wifiSsid;
        private UUID displayedFrameId;
    }
}
