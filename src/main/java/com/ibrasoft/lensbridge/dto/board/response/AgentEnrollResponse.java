package com.ibrasoft.lensbridge.dto.board.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AgentEnrollResponse {

    private UUID deviceId;

    /** Absolute URL the agent should connect to for the persistent command channel. */
    private String websocketUrl;

    /**
     * Public content signing keys the agent pins at enrollment, over the same TLS connection
     * that establishes its identity. Always present; empty when the server has no content
     * key configured.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SigningKeyView> contentSigningKeys;
}
