package com.ibrasoft.lensbridge.model.board.commands;

/**
 * Check the release channels and install a new board app or agent now, instead of in the
 * board's nightly install window. The board shows its update screen while it installs, and
 * an agent update restarts the agent.
 */
public record UpdateInstallNowPayload() implements CommandPayload {
    @Override public String kind() { return "update.install_now"; }
}
