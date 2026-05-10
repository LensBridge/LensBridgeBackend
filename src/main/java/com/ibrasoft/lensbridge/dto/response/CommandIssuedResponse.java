package com.ibrasoft.lensbridge.dto.response;

import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;

import java.time.Instant;
import java.util.UUID;

public record CommandIssuedResponse(
        UUID commandId,
        UUID deviceId,
        String kind,
        DeviceCommandStatus status,
        Instant issuedAt
) {}
