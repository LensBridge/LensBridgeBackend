package com.ibrasoft.lensbridge.dto.board.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GET /api/agent/signing-keys}. Only the {@code content} role exists here: release keys
 * are compiled into the agent and never come from the backend, which is the point of having
 * two trust roots.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SigningKeysResponse {

    /** Current content key first, then any previous keys still valid during a rotation. Empty when signing is not configured. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SigningKeyView> content;
}
