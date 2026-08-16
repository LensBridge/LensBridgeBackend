package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.minbar.Audience;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update: a null field means "leave it alone", so nothing here is
 * {@code @NotNull}. The constraints below therefore describe what a <em>supplied</em>
 * value must look like — Bean Validation skips them all for null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDeviceRequest {

    private Audience audience;

    /**
     * {@code @Size(min = 1)} alone would let {@code "   "} through, and the column is
     * only {@code nullable = false} — a whitespace name persists happily and leaves the
     * device looking unnamed in every admin listing. The pattern requires at least one
     * non-whitespace character; {@code BoardService} trims before persisting.
     */
    @Size(max = 255, message = "displayName must be at most 255 characters")
    @Pattern(regexp = ".*\\S.*", message = "displayName must not be blank")
    private String displayName;
}
