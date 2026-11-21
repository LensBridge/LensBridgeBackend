package com.ibrasoft.lensbridge.dto.board.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code GET /api/admin/board/devices/setup-defaults}: what a setup file or USB kit is when the
 * console does not say.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetupDefaultsResponse {

    /** Setup files are online ones by default: the server has a backend URL for boards. */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean onlineSetup;
}
