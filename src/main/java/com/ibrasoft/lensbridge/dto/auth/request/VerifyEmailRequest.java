package com.ibrasoft.lensbridge.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequest {

    @NotBlank(message = "Verification token is required")
    private String token;
}
