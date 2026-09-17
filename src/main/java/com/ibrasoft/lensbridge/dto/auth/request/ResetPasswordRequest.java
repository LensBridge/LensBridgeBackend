package com.ibrasoft.lensbridge.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for POST /api/auth/reset-password.
 *
 * Both fields were previously query parameters. A URL query string is recorded in
 * server access logs, proxy logs and browser history, so the reset token and the
 * new password were being written to places that outlive the request and are not
 * treated as secret storage. A request body is not logged by default.
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 256, message = "Password must be between 6 and 256 characters long")
    private String newPassword;
}
