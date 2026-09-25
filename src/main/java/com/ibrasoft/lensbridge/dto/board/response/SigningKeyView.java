package com.ibrasoft.lensbridge.dto.board.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One public signing key as boards pin it in {@code /etc/musallahboard/trust.json}. Same
 * shape wherever a key is handed out: enrollment's {@code contentSigningKeys} and
 * {@code GET /api/agent/signing-keys}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SigningKeyView {

    /** Lowercase hex of the first 8 bytes of SHA-256 over the raw public key. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0123456789abcdef")
    private String keyId;

    /** Base64 of the raw 32-byte Ed25519 public key. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String publicKey;
}
