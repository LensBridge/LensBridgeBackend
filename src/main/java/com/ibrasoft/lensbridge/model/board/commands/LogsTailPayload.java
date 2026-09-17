package com.ibrasoft.lensbridge.model.board.commands;

/** Tail the last {@code lines} entries from the agent + kiosk journald units. Capped at 500. */
public record LogsTailPayload(Integer lines) implements CommandPayload {
    public LogsTailPayload {
        if (lines != null && (lines < 1 || lines > 500)) {
            throw new IllegalArgumentException("lines must be between 1 and 500");
        }
    }
    @Override public String kind() { return "logs.tail"; }
}
