package com.ibrasoft.lensbridge.model.board.commands;

/** Trigger an in-page re-fetch of the board payload (no full reload). */
public record ConfigRefreshPayload() implements CommandPayload {
    @Override public String kind() { return "config.refresh"; }
}
