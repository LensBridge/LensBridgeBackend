package com.ibrasoft.lensbridge.dto.board.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentEnrollRequest {

    /** Plaintext one-time token issued by an admin. */
    @NotBlank
    private String token;

    /** Base64-encoded 32-byte Ed25519 public key generated on the device. */
    @NotBlank
    private String publicKey;

    // The three below are stored as-is in varchar(255) columns, which Postgres enforces: an
    // over-long value must be a 400 here rather than a 500 from the insert.
    @NotBlank
    @Size(max = 255, message = "hostname must be at most 255 characters")
    private String hostname;

    @Size(max = 255, message = "hardwareModel must be at most 255 characters")
    private String hardwareModel;

    @Size(max = 255, message = "agentVersion must be at most 255 characters")
    private String agentVersion;
}
