package com.ibrasoft.lensbridge.model.minbar.board.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ibrasoft.lensbridge.model.minbar.board.frames.FrameConfig;

/**
 * Polymorphic command payload, mirroring the {@link FrameConfig}
 * pattern: adding a new command kind = new record + new {@code @Type} entry + agent-side handler.
 * <p>
 * The discriminator is {@code kind} (e.g. {@code "chrome.reload"}). Stored as JSON in
 * {@code device_commands.payload_json} and re-parsed when delivering to the agent.
 * <p>
 * <b>This hierarchy is the single source of truth for payload shape on the issue path.</b>
 * {@code CommandPayloadCodec} resolves the record from the request's {@code kind} via
 * {@link CommandKind#getPayloadType()}, deserializes into it while rejecting unknown
 * properties, runs Jakarta Bean Validation, and re-serializes the validated object as
 * what gets persisted and pushed to the agent. Nothing an operator typed reaches the
 * device verbatim, so a constraint annotation added to a component here is enforced on
 * the wire with no further wiring. Declare bounds as constraint annotations rather than
 * as compact-constructor checks: the codec reports the former as a clean 400.
 * <p>
 * The closed {@code @JsonSubTypes} registry below is a deliberate anti-gadget defence.
 * Never replace it with {@code Id.CLASS}/{@code MINIMAL_CLASS} or enable default typing.
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
