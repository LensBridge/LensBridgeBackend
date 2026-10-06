package com.ibrasoft.lensbridge.dto.board.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IssueEnrollmentTokenRequest {

    /** Becomes devices.display_name, a varchar(255). */
    @NotBlank
    @Size(min = 1, max = 255)
    private String displayName;

    @NotNull
    private Audience audience;

    /** Lifetime of the token in minutes. Server clamps to a sane range. */
    @Positive
    private Integer expiresInMinutes;
}
