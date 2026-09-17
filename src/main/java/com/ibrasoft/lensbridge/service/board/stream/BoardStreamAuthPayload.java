package com.ibrasoft.lensbridge.service.board.stream;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Canonical byte sequence a board signs to join the refresh stream.
 * <p>
 * Deliberately a different domain string from the agent channel's
 * {@link com.ibrasoft.lensbridge.service.agent.handshake.AuthSignaturePayload}: a signature
 * produced for one channel must never verify on the other. The server-issued
 * {@code sessionId} and {@code challenge} already make cross-channel replay fail, but domain
 * separation is the backstop if either channel's handshake is ever relaxed.
 *
 * <pre>
 * musallahboard-stream-auth-v1\n
 * &lt;sessionId&gt;\n
 * &lt;challenge-base64&gt;\n
 * &lt;deviceId&gt;\n
 * &lt;timestamp-millis&gt;
 * </pre>
 *
 * Both sides MUST construct it identically. The version prefix lets the format change later
 * without silently accepting older agents — they will simply fail verification.
 */
public final class BoardStreamAuthPayload {

    public static final String VERSION_PREFIX = "musallahboard-stream-auth-v1";

    private BoardStreamAuthPayload() {}

    public static byte[] build(UUID sessionId, String challengeBase64, UUID deviceId, long timestampMillis) {
        String s = VERSION_PREFIX + "\n"
                + sessionId + "\n"
                + challengeBase64 + "\n"
                + deviceId + "\n"
                + timestampMillis;
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
