package com.ibrasoft.lensbridge.dto.board.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Backend → agent frame envelope. Built up via the builder for each emission;
 * Jackson serializes to {@code type, seq, sessionId, ...payload}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingAgentFrame {

    private String type;
    private long seq;
    private UUID sessionId;

    // ── hello ────────────────────────────────────────────────────────────
    /** Base64-encoded 32 random bytes. Single-use, scoped to this session. */
    private String challenge;
    private Long serverTime;

    // ── auth_ok ──────────────────────────────────────────────────────────
    private UUID deviceId;
    private Integer heartbeatIntervalMs;

    // ── command ──────────────────────────────────────────────────────────
    private UUID commandId;
    private String kind;
    private String issuedBy;
    private Integer deadlineMs;
    /** Serialized command payload (typically a {@link com.fasterxml.jackson.databind.JsonNode}). */
    private Object payload;

    // ── error ────────────────────────────────────────────────────────────
    private String error;
    private String reason;
}
