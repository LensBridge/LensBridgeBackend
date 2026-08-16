package com.ibrasoft.lensbridge.model.minbar.board.commands;

import com.ibrasoft.lensbridge.model.auth.Permission;

import java.util.Arrays;
import java.util.Optional;

/**
 * The registry of issuable command kinds and their {@link CommandRisk}.
 * <p>
 * This replaces the hardcoded {@code KNOWN_KINDS} string set that used to live in
 * {@code CommandDispatcher}, which duplicated the {@code @JsonSubTypes} registration on
 * {@link CommandPayload} with nothing keeping the two in step. Adding a command kind is
 * now: new record + new {@code @Type} entry + a constant here (which forces a risk
 * classification) + agent-side handler. {@code CommandKindTest} fails if the wire strings
 * here and the Jackson subtypes ever diverge.
 * <p>
 * The dispatcher never deserializes the payload into a {@link CommandPayload}; it stores
 * the raw JSON and hands it to the agent so classification has to hang off the wire
 * string rather than the payload type.
 */
public enum CommandKind {

    CHROME_RELOAD("chrome.reload", CommandRisk.BENIGN),
    CONFIG_REFRESH("config.refresh", CommandRisk.BENIGN),

    KIOSK_RESTART("kiosk.restart", CommandRisk.DISRUPTIVE),
    SYSTEM_REBOOT("system.reboot", CommandRisk.DISRUPTIVE),

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
