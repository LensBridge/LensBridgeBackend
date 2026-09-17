package com.ibrasoft.lensbridge.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body for POST /api/auth/validate-reset-token.
 *
 * Deliberately separate from {@link VerifyEmailRequest}, which is also a lone
 * token field: the two carry different credentials, and sharing one type would
 * name the wrong thing in every generated client.
 */
@Data
public class ValidateResetTokenRequest {

    @NotBlank(message = "Reset token is required")
    private String token;
}
