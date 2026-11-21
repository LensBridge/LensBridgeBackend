package com.ibrasoft.lensbridge.model.board.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibrasoft.lensbridge.model.auth.Permission;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The registry of issuable command kinds: each one's {@link CommandRisk}, the longest it may
 * wait undelivered for an offline board, and its payload rule ({@link CommandPayloads}).
 * <p>
 * Authorization (see {@code CommandAuthorizer}) hangs off the wire string alone. Adding a
 * command kind is a constant here, which forces a risk classification, a maximum time-to-live
 * and a payload rule, plus the agent-side handler.
 * <p>
 * The time-to-live is short for disruptive kinds on purpose: a reboot or kiosk restart that
 * fires when a board comes back from a long outage is a surprise in a prayer space, not a
 * delayed success. Benign kinds may wait longer; a refresh when the board returns is useful.
 */
public enum CommandKind {

    CHROME_RELOAD("chrome.reload", CommandRisk.BENIGN, Duration.ofHours(1), CommandPayloads::none),
    CONFIG_REFRESH("config.refresh", CommandRisk.BENIGN, Duration.ofHours(1), CommandPayloads::none),

    KIOSK_RESTART("kiosk.restart", CommandRisk.DISRUPTIVE, Duration.ofMinutes(10), CommandPayloads::none),
    SYSTEM_REBOOT("system.reboot", CommandRisk.DISRUPTIVE, Duration.ofMinutes(10), CommandPayloads::none),
    /** Takes the board off its content for the update screen, and may restart the agent. */
    UPDATE_INSTALL_NOW("update.install_now", CommandRisk.DISRUPTIVE, Duration.ofMinutes(10), CommandPayloads::none),

    /** Captures whatever is on the screen when it runs, so it must not run long after it was asked for. */
    CHROME_SCREENSHOT("chrome.screenshot", CommandRisk.INSPECT, Duration.ofMinutes(15), CommandPayloads::none),
    LOGS_TAIL("logs.tail", CommandRisk.INSPECT, Duration.ofMinutes(15), CommandPayloads::logsTail);

    private final String wireName;
    private final CommandRisk risk;
    private final Duration maxTtl;
    private final CommandPayloads.Rule payloadRule;

    CommandKind(String wireName, CommandRisk risk, Duration maxTtl, CommandPayloads.Rule payloadRule) {
        this.wireName = wireName;
        this.risk = risk;
        this.maxTtl = maxTtl;
        this.payloadRule = payloadRule;
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

    /** The longest this kind may stay deliverable while its board is offline. */
    public Duration getMaxTtl() {
        return maxTtl;
    }

    /**
     * Checks a payload for this kind.
     *
     * @param payload as the admin sent it; null when absent
     * @return the payload to store and send to the board (JSON null for none)
     * @throws InvalidCommandException when this kind does not accept it
     */
    public JsonNode validatePayload(JsonNode payload) {
        return payloadRule.check(this, payload);
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
