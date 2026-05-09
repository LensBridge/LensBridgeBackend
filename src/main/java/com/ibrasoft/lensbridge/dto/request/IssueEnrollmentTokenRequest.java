package com.ibrasoft.lensbridge.dto.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class IssueEnrollmentTokenRequest {

    @NotBlank
    private String displayName;

    @NotNull
    private Audience audience;

    /** Lifetime of the token in minutes. Server clamps to a sane range. */
    @Positive
    private Integer expiresInMinutes;
}
