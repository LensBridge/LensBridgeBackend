package com.ibrasoft.lensbridge.dto.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Agent → backend: optional progress update for a long-running command. */
@Getter
@Setter
public class CommandProgressFrame extends IncomingAgentFrame {
    private UUID commandId;
    private String stage;
    private String message;
    /** 0..100, optional. */
    private Integer percent;
}
