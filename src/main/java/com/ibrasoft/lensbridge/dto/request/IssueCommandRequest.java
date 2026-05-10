package com.ibrasoft.lensbridge.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Admin request to issue a command at a single device.
 * <p>
 * {@code kind} is the polymorphic discriminator (must match a registered
 * {@link com.ibrasoft.lensbridge.model.board.commands.CommandPayload} subtype).
 * {@code payload} is the kind-specific JSON body (may be empty for parameterless commands).
 */
public record IssueCommandRequest(
        @NotBlank
        @Pattern(regexp = "[a-z]+\\.[a-z_]+", message = "kind must be in dotted lowercase form, e.g. chrome.reload")
        String kind,
        JsonNode payload,
        Integer deadlineMs
) {}
