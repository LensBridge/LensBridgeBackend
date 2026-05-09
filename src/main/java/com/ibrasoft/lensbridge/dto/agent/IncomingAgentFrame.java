package com.ibrasoft.lensbridge.dto.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

/**
 * Polymorphic envelope for frames sent from the agent to the backend.
 * Discriminated by the {@code type} field.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AuthFrame.class,      name = "auth"),
        @JsonSubTypes.Type(value = HeartbeatFrame.class, name = "heartbeat")
})
public abstract class IncomingAgentFrame {

    /** Discriminator (kept for logging / rejection messages even though Jackson uses it as the type id). */
    private String type;

    /** Monotonic sequence number, starts at 1 from the agent. */
    private long seq;

    /** Session identifier issued by the backend in the {@code hello} frame. */
    private UUID sessionId;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
}
