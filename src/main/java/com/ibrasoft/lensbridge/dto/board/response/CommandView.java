package com.ibrasoft.lensbridge.dto.board.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommand;
import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommandStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a {@link DeviceCommand} for admin REST responses.
 *
 * <p>{@code output} is the agent's raw command result. For {@code chrome.screenshot} that is a
 * captured image of a physical display in a prayer space, and for {@code logs.tail} it is agent
 * internals — the same data the live STOMP channel gates behind
 * {@link com.ibrasoft.lensbridge.model.auth.Permission#BOARD_TELEMETRY_SUBSCRIBE}. Command
 * metadata (kind, status, timestamps, issuer) is fleet status and is fine at
 * {@link com.ibrasoft.lensbridge.model.auth.Permission#BOARD_DEVICE_READ}; the payload of the
 * result is not. Hence {@code includeOutput} is an explicit argument rather than a default:
 * every call site has to state which of the two it is serving, so nobody replays screenshots
 * through the history endpoint by forgetting to opt out.
 *
 * @param outputRedacted true when this command produced output that the caller was not
 *                       permitted to see. Lets a {@code BOARD_DEVICE_READ} operator tell
 *                       "a screenshot was captured, you may not view it" apart from
 *                       "this command returned nothing".
 */
public record CommandView(
        UUID id,
        UUID deviceId,
        String kind,
        JsonNode payload,
        String issuedBy,
        Integer deadlineMs,
        DeviceCommandStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant deliveredAt,
        Instant ackedAt,
        Instant startedAt,
        Instant finishedAt,
        JsonNode output,
        boolean outputRedacted,
        String errorMessage
) {
    /**
     * @param includeOutput whether the caller holds
     *                      {@link com.ibrasoft.lensbridge.model.auth.Permission#BOARD_TELEMETRY_SUBSCRIBE}.
     *                      When false, {@code output} is withheld and {@code outputRedacted}
     *                      reports whether anything was withheld.
     */
    public static CommandView of(DeviceCommand cmd, ObjectMapper mapper, boolean includeOutput) {
        boolean hasOutput = isPresent(cmd.getOutputJson());
        return new CommandView(
                cmd.getId(),
                cmd.getDeviceId(),
                cmd.getKind(),
                parseOrNull(cmd.getPayloadJson(), mapper),
                cmd.getIssuedBy(),
                cmd.getDeadlineMs(),
                cmd.getStatus(),
                cmd.getIssuedAt(),
                cmd.getExpiresAt(),
                cmd.getDeliveredAt(),
                cmd.getAckedAt(),
                cmd.getStartedAt(),
                cmd.getFinishedAt(),
                includeOutput ? parseOrNull(cmd.getOutputJson(), mapper) : null,
                !includeOutput && hasOutput,
                cmd.getErrorMessage()
        );
    }

    private static boolean isPresent(String json) {
        return json != null && !json.isBlank();
    }

    private static JsonNode parseOrNull(String json, ObjectMapper mapper) {
        if (!isPresent(json)) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
