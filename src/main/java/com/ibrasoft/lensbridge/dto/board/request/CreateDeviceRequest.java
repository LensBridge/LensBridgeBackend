package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/admin/board/devices}: a board created in the console, before it has
 * ever been switched on. It has no key until it claims online; an offline board never does.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeviceRequest {

    /** Shown on the board when its setup file is applied ("Set up as North Musallah"). */
    @NotBlank
    @Size(max = 255, message = "displayName must be at most 255 characters")
    private String displayName;

    @NotNull
    private Audience audience;

    /**
     * Where the board is, for its prayer times. Optional: fields left out keep the defaults
     * (the same location a token-enrolled board starts with), and can be edited later.
     */
    private Location location;
}
