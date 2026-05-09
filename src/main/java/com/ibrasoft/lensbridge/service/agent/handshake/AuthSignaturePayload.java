package com.ibrasoft.lensbridge.service.agent.handshake;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Canonical byte sequence the agent signs at handshake time.
 * <p>
 * Both sides MUST construct it identically — the protocol version prefix lets us
 * change the format in the future without breaking older agents (they will simply
 * fail verification, which is the correct behaviour).
 *
 * <pre>
 * musallahboard-auth-v1\n
 * &lt;sessionId&gt;\n
 * &lt;challenge-base64&gt;\n
 * &lt;deviceId&gt;\n
 * &lt;timestamp-millis&gt;
 * </pre>
 */
public final class AuthSignaturePayload {

    public static final String VERSION_PREFIX = "musallahboard-auth-v1";

    private AuthSignaturePayload() {}

    public static byte[] build(UUID sessionId, String challengeBase64, UUID deviceId, long timestampMillis) {
        String s = VERSION_PREFIX + "\n"
                + sessionId + "\n"
                + challengeBase64 + "\n"
                + deviceId + "\n"
                + timestampMillis;
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
