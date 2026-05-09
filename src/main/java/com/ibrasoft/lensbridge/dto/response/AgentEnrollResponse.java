package com.ibrasoft.lensbridge.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AgentEnrollResponse {

    private UUID deviceId;

    /** Absolute URL the agent should connect to for the persistent command channel. */
    private String websocketUrl;
}
