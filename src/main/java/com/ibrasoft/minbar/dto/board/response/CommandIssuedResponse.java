package com.ibrasoft.minbar.dto.board.response;

import com.ibrasoft.minbar.model.board.DeviceCommandStatus;

import java.time.Instant;
import java.util.UUID;

public record CommandIssuedResponse(
        UUID commandId,
        UUID deviceId,
        String kind,
        DeviceCommandStatus status,
        Instant issuedAt
) {}
