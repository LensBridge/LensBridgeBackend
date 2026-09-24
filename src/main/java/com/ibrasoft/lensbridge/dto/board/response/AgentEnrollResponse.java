package com.ibrasoft.lensbridge.dto.board.response;

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
     * that establishes its identity. Empty when the server has no content key configured.
     * Optional in the spec so agents built before it existed still decode the response.
     */
    private List<SigningKeyView> contentSigningKeys;
}
