package com.ibrasoft.lensbridge.model.board;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What a board reports about itself with every heartbeat: the board app it runs, the content
 * it shows, the software waiting for its nightly install window, how its last agent update
 * went, and whether its clock can be believed. Stored as the device's latest state, it is the
 * admin portal's view of a board nobody is standing in front of.
 * <p>
 * The agent owns this shape ({@code localserver.BoardReport} in the agent repo). Unknown
 * fields are ignored so a newer agent never breaks an older backend's heartbeat parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardReport(
        String appVersion,
        Content content,
        Updates updates,
        AgentUpdate lastAgentUpdate,
        Clock clock,
        /* Why the last content sync failed, if it did. */
        String syncError,
        /* Set when the installed content cannot be read. */
        String error
) {

    /** The installed content. Dates are the board's own ({@code yyyy-MM-dd}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String firstDay,
            String lastDay,
            String createdAt,
            /* How it arrived: sync, usb, upload or cli. */
            String source,
            String installedAt,
            int daysRemaining,
            int staleDays
    ) {}

    /** Board software waiting for the install window. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Updates(
            List<Update> available,
            /* "HH:mm" in the board's zone. */
            String installTime,
            /* When the waiting updates install, RFC 3339; null when nothing waits. */
            String installAt,
            boolean installing
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(String type, String version, String description) {}

    /** The root updater's last outcome: status is ok, rolled-back or refused. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentUpdate(String from, String to, String at, String status, String message) {}

    /**
     * Where the clock's time comes from: ntp, rtc, uploader, starting or unverified.
     * {@code trusted} is false when the board is warning that its clock may be wrong.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clock(String source, boolean trusted) {}
}
