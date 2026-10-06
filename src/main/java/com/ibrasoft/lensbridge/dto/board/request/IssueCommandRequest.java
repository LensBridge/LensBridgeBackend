package com.ibrasoft.lensbridge.dto.board.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Admin request to issue a command at a single device.
 * <p>
 * {@code kind} is the command's wire name and must be one of
 * {@link com.ibrasoft.lensbridge.model.board.commands.CommandKind}; anything else is a 400.
 * {@code payload} is the kind-specific JSON body (may be empty for parameterless commands). It
 * is stored and forwarded as given, not interpreted here: the agent validates it.
 * <p>
 * {@code deadlineMs} bounds execution once the agent starts. {@code ttlSeconds} bounds how
 * long the command stays deliverable while the device is offline. Keep it short for
 * anything disruptive (a reboot that fires a day late is a bug, not a feature ;) ). Both fall
 * back to server defaults when null (30 s to execute, 5 min to be delivered).
 */
public record IssueCommandRequest(
        @NotBlank
        @Pattern(regexp = "[a-z]+\\.[a-z_]+", message = "kind must be in dotted lowercase form, e.g. chrome.reload")
        String kind,
        JsonNode payload,
        @Positive Integer deadlineMs,
        @Positive Integer ttlSeconds
) {}
