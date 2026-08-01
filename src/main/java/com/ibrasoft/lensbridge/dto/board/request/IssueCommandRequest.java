package com.ibrasoft.lensbridge.dto.board.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Admin request to issue a command at a single device.
 * <p>
 * {@code kind} is the polymorphic discriminator (must match a registered
 * {@link com.ibrasoft.lensbridge.model.board.commands.CommandPayload} subtype).
 * {@code payload} is the kind-specific JSON body (may be empty for parameterless commands).
 * <p>
 * {@code deadlineMs} bounds execution once the agent starts. {@code ttlSeconds} bounds how
 * long the command stays deliverable while the device is offline. Keep it short for
 * anything disruptive (a reboot that fires a day late is a bug, not a feature ;) ). Both fall
 * back to server defaults when null.
 */
public record IssueCommandRequest(
        @NotBlank
        @Pattern(regexp = "[a-z]+\\.[a-z_]+", message = "kind must be in dotted lowercase form, e.g. chrome.reload")
        String kind,
        JsonNode payload,
        @Positive Integer deadlineMs,
        @Positive Integer ttlSeconds
) {}
