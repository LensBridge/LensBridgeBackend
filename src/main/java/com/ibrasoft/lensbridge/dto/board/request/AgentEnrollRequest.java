package com.ibrasoft.lensbridge.dto.board.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentEnrollRequest {

    /** Plaintext one-time token issued by an admin. */
    @NotBlank
    private String token;

    /** Base64-encoded 32-byte Ed25519 public key generated on the device. */
    @NotBlank
    private String publicKey;

    @NotBlank
    private String hostname;

    private String hardwareModel;
    private String agentVersion;
}
