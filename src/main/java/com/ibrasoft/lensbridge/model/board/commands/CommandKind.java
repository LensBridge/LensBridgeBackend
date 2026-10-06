package com.ibrasoft.lensbridge.model.board.commands;

import com.ibrasoft.lensbridge.model.auth.Permission;

import java.util.Arrays;
import java.util.Optional;

/**
 * The registry of issuable command kinds and their {@link CommandRisk}.
 * <p>
 * The dispatcher never interprets a command's payload: it stores the raw JSON and hands it to
 * the agent, which owns each kind's payload shape and validation. So classification, and with
 * it authorization (see {@code CommandAuthorizer}), hangs off the wire string alone. Adding a
 * command kind is a constant here (which forces a risk classification) plus the agent-side
 * handler.
 */
public enum CommandKind {

    CHROME_RELOAD("chrome.reload", CommandRisk.BENIGN),
    CONFIG_REFRESH("config.refresh", CommandRisk.BENIGN),

    KIOSK_RESTART("kiosk.restart", CommandRisk.DISRUPTIVE),
    SYSTEM_REBOOT("system.reboot", CommandRisk.DISRUPTIVE),
    /** Takes the board off its content for the update screen, and may restart the agent. */
    UPDATE_INSTALL_NOW("update.install_now", CommandRisk.DISRUPTIVE),

    CHROME_SCREENSHOT("chrome.screenshot", CommandRisk.INSPECT),
    LOGS_TAIL("logs.tail", CommandRisk.INSPECT);

    private final String wireName;
    private final CommandRisk risk;

    CommandKind(String wireName, CommandRisk risk) {
        this.wireName = wireName;
        this.risk = risk;
    }

    /** The dotted discriminator sent over the wire, e.g. {@code chrome.reload}. */
    public String getWireName() {
        return wireName;
    }

    public CommandRisk getRisk() {
        return risk;
    }

    public Permission getRequiredPermission() {
        return risk.getRequiredPermission();
    }

    public static Optional<CommandKind> from(String wireName) {
        if (wireName == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(k -> k.wireName.equals(wireName))
                .findFirst();
    }

    @Override
    public String toString() {
        return wireName;
    }
}
