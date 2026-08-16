package com.ibrasoft.lensbridge.model.minbar.board.commands;

/** Restart the {@code musallahboard-kiosk.service} systemd unit on the Pi. */
public record KioskRestartPayload() implements CommandPayload {
    @Override public String kind() { return "kiosk.restart"; }
}
