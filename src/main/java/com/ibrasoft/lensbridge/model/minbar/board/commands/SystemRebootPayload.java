package com.ibrasoft.lensbridge.model.minbar.board.commands;

/** Reboot the host. Agent acks + reports {@code ok} *before* invoking shutdown. */
public record SystemRebootPayload() implements CommandPayload {
    @Override public String kind() { return "system.reboot"; }
}
