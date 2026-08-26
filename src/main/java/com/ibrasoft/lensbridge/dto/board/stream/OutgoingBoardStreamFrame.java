package com.ibrasoft.lensbridge.dto.board.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Backend → board frame envelope for the refresh stream.
 * <p>
 * Three shapes ride this envelope: {@code hello} (challenge), {@code auth_ok}, and
 * {@code refresh}. The {@code refresh} shape keeps the exact field names the deployed kiosk
 * already reads ({@code type}, {@code reason}, {@code deviceId}, {@code at}); {@code seq} and
 * {@code sessionId} are additive and safe for an older reader to ignore.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingBoardStreamFrame {

    private String type;
    private long seq;
    private UUID sessionId;

    // ── hello ────────────────────────────────────────────────────────────
    /** Base64-encoded 32 random bytes. Single-use, scoped to this session. */
    private String challenge;
    private Long serverTime;

    // ── auth_ok / refresh ────────────────────────────────────────────────
    /** On auth_ok: the device this connection is now bound to. On refresh: the addressed
     *  board, or absent for a broadcast. */
    private UUID deviceId;

    // ── refresh ──────────────────────────────────────────────────────────
    /** Why the board should refetch. */
    private String reason;
    /**
     * ISO-8601 instant, pre-formatted as a String rather than left as an {@code Instant}: a
     * mapper without JavaTimeModule throws on one, and the only symptom would be pushes
     * silently going nowhere.
     */
    private String at;
}
