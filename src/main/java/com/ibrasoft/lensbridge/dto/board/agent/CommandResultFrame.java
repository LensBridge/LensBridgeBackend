package com.ibrasoft.lensbridge.dto.board.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibrasoft.lensbridge.model.minbar.board.DeviceCommandStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Agent → backend: terminal frame for a command. {@link #status} is one of
 * {@code "ok"}, {@code "error"}, {@code "timeout"}, {@code "rejected"} (free-form
 * to keep the wire flexible; the backend maps to {@link DeviceCommandStatus}).
 */
@Getter
@Setter
public class CommandResultFrame extends IncomingAgentFrame {
    private UUID commandId;
    private String status;
    private JsonNode output;
    private String errorMessage;
    private Long durationMs;
}
