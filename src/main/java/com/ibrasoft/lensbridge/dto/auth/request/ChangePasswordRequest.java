package com.ibrasoft.lensbridge.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank
    @Size(min = 6, max = 256)
    private String currentPassword;

    @NotBlank
    @Size(min = 6, max = 256)
    private String newPassword;
}
