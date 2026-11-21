package com.ibrasoft.lensbridge.dto.board.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Admin request to issue a command at a single device.
 * <p>
 * {@code kind} is the command's wire name and must be one of
 * {@link com.ibrasoft.lensbridge.model.board.commands.CommandKind}; anything else is a 400.
 * {@code payload} is the kind-specific JSON body, checked against the kind's rule when the
 * command is issued: parameterless kinds take none (absent, null or {@code {}}), and
 * {@code logs.tail} takes {@code {"lines": 1..500}}. Anything else is a 400.
 * <p>
 * {@code deadlineMs} bounds execution once the agent starts, at most {@value #MAX_DEADLINE_MS}
 * ms. {@code ttlSeconds} bounds how long the command stays deliverable while the device is
 * offline, at most the kind's own limit (ten minutes for disruptive kinds such as
 * {@code system.reboot}, an hour at most for any kind). Both fall back to server defaults
 * when null (30 s to execute, 5 min to be delivered).
 */
public record IssueCommandRequest(
        @NotBlank
        @Pattern(regexp = "[a-z]+\\.[a-z_]+", message = "kind must be in dotted lowercase form, e.g. chrome.reload")
        String kind,
        JsonNode payload,
        @Positive @Max(value = MAX_DEADLINE_MS, message = "deadlineMs must be at most " + MAX_DEADLINE_MS)
        Integer deadlineMs,
        @Positive @Max(value = MAX_TTL_SECONDS, message = "ttlSeconds must be at most " + MAX_TTL_SECONDS
                + "; disruptive commands allow less")
        Integer ttlSeconds
) {
    /**
     * Longest execution deadline for any command: 15 minutes. The admin console gives
     * {@code update.install_now} 10 minutes to download and install a release.
     */
    public static final int MAX_DEADLINE_MS = 15 * 60 * 1000;

    /** Longest time-to-live of any kind (the benign ones); each kind may allow less. */
    public static final int MAX_TTL_SECONDS = 60 * 60;
}
