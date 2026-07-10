package com.ibrasoft.minbar.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequest {
    
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
