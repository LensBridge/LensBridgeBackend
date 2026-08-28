package com.ibrasoft.lensbridge.dto.minbar.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * One walking instruction. Position comes from the list order, not from a field —
 * a client that sends steps out of order and numbers them separately can disagree
 * with itself, and the server would have no way to tell which it meant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectionStepRequest {

    @NotBlank(message = "Each step needs an instruction")
    @Size(max = 255, message = "Instruction must be 255 characters or fewer")
    private String instruction;

    /** Optional landmark confirming the step was followed correctly. */
    @Size(max = 255, message = "Landmark must be 255 characters or fewer")
    private String subtext;
}
