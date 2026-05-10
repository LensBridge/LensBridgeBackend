package com.ibrasoft.lensbridge.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.board.DeviceCommandStatus;

import java.time.Instant;
import java.util.UUID;

/** Read-only view of a {@link DeviceCommand} for admin REST responses. */
public record CommandView(
        UUID id,
        UUID deviceId,
        String kind,
        JsonNode payload,
        String issuedBy,
        Integer deadlineMs,
        DeviceCommandStatus status,
        Instant issuedAt,
        Instant deliveredAt,
        Instant ackedAt,
        Instant startedAt,
        Instant finishedAt,
        JsonNode output,
        String errorMessage
) {
    public static CommandView of(DeviceCommand cmd, ObjectMapper mapper) {
        return new CommandView(
                cmd.getId(),
                cmd.getDeviceId(),
                cmd.getKind(),
                parseOrNull(cmd.getPayloadJson(), mapper),
                cmd.getIssuedBy(),
                cmd.getDeadlineMs(),
                cmd.getStatus(),
                cmd.getIssuedAt(),
                cmd.getDeliveredAt(),
                cmd.getAckedAt(),
                cmd.getStartedAt(),
                cmd.getFinishedAt(),
                parseOrNull(cmd.getOutputJson(), mapper),
                cmd.getErrorMessage()
        );
    }

    private static JsonNode parseOrNull(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
