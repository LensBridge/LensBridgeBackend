package com.ibrasoft.lensbridge.model.board.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic command payload, mirroring the {@link com.ibrasoft.lensbridge.model.board.frames.FrameConfig}
 * pattern: adding a new command kind = new record + new {@code @Type} entry + agent-side handler.
 * <p>
 * The discriminator is {@code kind} (e.g. {@code "chrome.reload"}). Stored as JSON in
 * {@code device_commands.payload_json} and re-parsed when delivering to the agent.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "kind"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChromeReloadPayload.class,     name = "chrome.reload"),
        @JsonSubTypes.Type(value = ChromeScreenshotPayload.class, name = "chrome.screenshot"),
        @JsonSubTypes.Type(value = KioskRestartPayload.class,     name = "kiosk.restart"),
        @JsonSubTypes.Type(value = SystemRebootPayload.class,     name = "system.reboot"),
        @JsonSubTypes.Type(value = ConfigRefreshPayload.class,    name = "config.refresh"),
        @JsonSubTypes.Type(value = LogsTailPayload.class,         name = "logs.tail"),
})
public sealed interface CommandPayload
        permits ChromeReloadPayload, ChromeScreenshotPayload, KioskRestartPayload,
                SystemRebootPayload, ConfigRefreshPayload, LogsTailPayload {

    String kind();
}
