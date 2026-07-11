package com.ibrasoft.minbar.signage.dto.response;

import com.ibrasoft.minbar.signage.model.DeviceCommandStatus;

import java.time.Instant;
import java.util.UUID;

public record CommandIssuedResponse(
        UUID commandId,
        UUID deviceId,
        String kind,
        DeviceCommandStatus status,
        Instant issuedAt
) {}
