package com.ibrasoft.lensbridge.dto.board.stream;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Board → backend authentication frame, sent in response to a {@code hello}.
 * <p>
 * The signature covers a fixed canonical string; see
 * {@link com.ibrasoft.lensbridge.service.board.stream.BoardStreamAuthPayload}. The public key
 * used to verify it is always the one on the enrolled {@code Device} row — never anything
 * carried in this frame.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class BoardStreamAuthFrame extends IncomingBoardStreamFrame {

    private UUID deviceId;

    /** Unix milliseconds; server enforces |timestamp - now| within a skew window. */
    private long timestamp;

    /** Base64-encoded 64-byte Ed25519 signature. */
    private String signature;
}
