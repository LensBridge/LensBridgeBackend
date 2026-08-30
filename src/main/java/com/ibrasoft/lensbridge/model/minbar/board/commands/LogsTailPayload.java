package com.ibrasoft.lensbridge.model.minbar.board.commands;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Tail the last {@code lines} entries from the agent + kiosk journald units.
 * <p>
 * The bound lives on the component as a constraint annotation and nowhere else.
 * {@code CommandPayloadCodec} runs Bean Validation on every issued payload, so this is
 * what the device actually receives — see {@link CommandPayload}. Null means "agent
 * default"; the agent picks its own line count.
 */
public record LogsTailPayload(
        @Min(value = 1, message = "lines must be between 1 and 500")
        @Max(value = 500, message = "lines must be between 1 and 500")
        Integer lines
) implements CommandPayload {
    @Override public String kind() { return "logs.tail"; }
}
