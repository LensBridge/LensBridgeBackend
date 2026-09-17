package com.ibrasoft.lensbridge.dto.board.agent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Agent → backend authentication frame, sent in response to a {@code hello}.
 * <p>
 * The signature covers a fixed canonical string. See {@link com.ibrasoft.lensbridge.service.agent.handshake.AuthSignaturePayload}.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AuthFrame extends IncomingAgentFrame {

    private UUID deviceId;

    /** Unix milliseconds; server enforces |timestamp - now| within a skew window. */
    private long timestamp;

    /** Base64-encoded 64-byte Ed25519 signature. */
    private String signature;
}
