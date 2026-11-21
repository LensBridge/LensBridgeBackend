package com.ibrasoft.lensbridge.dto.board.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Body of {@code POST /api/agent/claim} (contract C2): a board set up by an online setup file
 * binds the device key it generated to its pre-created device.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentClaimRequest {

    /** The deviceId from the board's setup file. */
    @NotNull
    private UUID deviceId;

    /** Base64 (standard) of the 32-byte Ed25519 public key the board generated. */
    @NotBlank
    @Size(max = 64)
    private String publicKey;

    /**
     * Base64url of HMAC-SHA256, keyed with the claim secret's 32 bytes, over
     * {@code deviceId + "\n" + publicKey}: the lowercase deviceId and the publicKey string
     * exactly as sent. Padding optional.
     */
    @NotBlank
    @Size(max = 64)
    private String proof;

    @Size(max = 255, message = "agentVersion must be at most 255 characters")
    private String agentVersion;

    @Size(max = 255, message = "hostname must be at most 255 characters")
    private String hostname;
}
