package com.ibrasoft.lensbridge.dto.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Agent → backend: "I received and accepted command {@code commandId}, will execute". */
@Getter
@Setter
public class CommandAckFrame extends IncomingAgentFrame {
    private UUID commandId;
}
