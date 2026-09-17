package com.ibrasoft.lensbridge.dto.auth.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class VerifyUserRequest {

    @NotNull(message = "userId is required")
    private UUID userId;
}
