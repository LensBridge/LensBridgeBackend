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
 * classification and names the payload record) + agent-side handler.
 * {@code CommandKindTest} fails if the wire strings, the payload types and the Jackson
 * subtypes ever diverge.
 * <p>
 * Each constant carries the {@link CommandPayload} record that defines its payload shape.
 * {@code CommandPayloadCodec} uses that mapping to deserialize and Bean-Validate the
 * incoming JSON before anything is stored or forwarded, so a bound declared on a payload
 * component is enforced on the wire without any further wiring.
 */
public enum CommandKind {

    CHROME_RELOAD("chrome.reload", CommandRisk.BENIGN, ChromeReloadPayload.class),
    CONFIG_REFRESH("config.refresh", CommandRisk.BENIGN, ConfigRefreshPayload.class),

    KIOSK_RESTART("kiosk.restart", CommandRisk.DISRUPTIVE, KioskRestartPayload.class),
    SYSTEM_REBOOT("system.reboot", CommandRisk.DISRUPTIVE, SystemRebootPayload.class),

    CHROME_SCREENSHOT("chrome.screenshot", CommandRisk.INSPECT, ChromeScreenshotPayload.class),
    LOGS_TAIL("logs.tail", CommandRisk.INSPECT, LogsTailPayload.class);

    private final String wireName;
    private final CommandRisk risk;
    private final Class<? extends CommandPayload> payloadType;

    CommandKind(String wireName, CommandRisk risk, Class<? extends CommandPayload> payloadType) {
        this.wireName = wireName;
        this.risk = risk;
        this.payloadType = payloadType;
    }

    /** The dotted discriminator sent over the wire, e.g. {@code chrome.reload}. */
    public String getWireName() {
        return wireName;
    }

    public CommandRisk getRisk() {
        return risk;
    }

    /** The record defining this kind's payload shape; the target type for validated deserialization. */
    public Class<? extends CommandPayload> getPayloadType() {
        return payloadType;
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
