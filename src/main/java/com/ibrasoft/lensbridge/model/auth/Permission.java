package com.ibrasoft.lensbridge.model.auth;

import org.springframework.security.core.GrantedAuthority;

/**
 * The unit of authorization. Every {@code @PreAuthorize} in this codebase checks a
 * permission via {@code hasAuthority(...)}; none of them check a role name.
 * {@link Role} exists only as a named bundle of these.
 * <p>
 * Authority strings are namespaced {@code domain:resource:verb} and carry <em>no</em>
 * {@code ROLE_} prefix, which is what distinguishes them from {@link Role} authorities
 * in the same granted-authority collection.
 * <p>
 * The literals live in {@link Authority} and are referenced from the constructor, rather
 * than the other way round, because {@code @PreAuthorize} needs compile-time constants.
 * Declaring them once here means the annotation sites and the enum cannot drift apart.
 */
public enum Permission implements GrantedAuthority {

    // ==================== Legacy media sharing ====================
    // These name what USER and ADMIN already do. Media authorization is unchanged;
    // it is only expressed as a bundle now so it composes like everything else.

    MEDIA_UPLOAD_SELF(Authority.MEDIA_UPLOAD_SELF),
    MEDIA_UPLOAD_MODERATE(Authority.MEDIA_UPLOAD_MODERATE),
    MEDIA_UPLOAD_READ(Authority.MEDIA_UPLOAD_READ),
    MEDIA_EVENT_WRITE(Authority.MEDIA_EVENT_WRITE),

    // ==================== MusallahBoard content ====================

    BOARD_CONTENT_READ(Authority.BOARD_CONTENT_READ),
    BOARD_POSTER_WRITE(Authority.BOARD_POSTER_WRITE),
    BOARD_EVENT_WRITE(Authority.BOARD_EVENT_WRITE),
    /** The social accounts promoted to a board. Carries a QR destination, so it publishes a link. */
    BOARD_SOCIAL_WRITE(Authority.BOARD_SOCIAL_WRITE),
    /** Quotes and jummah times together — they share one endpoint. See docs/PERMISSIONS.md §3. */
    BOARD_WEEKLY_WRITE(Authority.BOARD_WEEKLY_WRITE),
    /** Ticker copy only. Deliberately not BOARD_CONFIG_WRITE, which can move the board's coordinates. */
    BOARD_TICKER_WRITE(Authority.BOARD_TICKER_WRITE),

    // ==================== MusallahBoard configuration ====================

    BOARD_CONFIG_READ(Authority.BOARD_CONFIG_READ),
    BOARD_CONFIG_WRITE(Authority.BOARD_CONFIG_WRITE),
    BOARD_REFRESH(Authority.BOARD_REFRESH),

    // ==================== Fleet ====================

    BOARD_DEVICE_READ(Authority.BOARD_DEVICE_READ),
    /** Mints a credential an unknown machine can exchange for a device identity. */
    BOARD_DEVICE_ENROLL(Authority.BOARD_DEVICE_ENROLL),
    BOARD_DEVICE_REVOKE(Authority.BOARD_DEVICE_REVOKE),
    /** Required to SUBSCRIBE to /topic/devices/** — command output lands there. */
    BOARD_TELEMETRY_SUBSCRIBE(Authority.BOARD_TELEMETRY_SUBSCRIBE),

    // ==================== Device commands, split by blast radius ====================

    /** Recoverable, no data egress: chrome.reload, config.refresh. */
    BOARD_COMMAND_BENIGN(Authority.BOARD_COMMAND_BENIGN),
    /** Takes a physical display down: kiosk.restart, system.reboot. */
    BOARD_COMMAND_DISRUPTIVE(Authority.BOARD_COMMAND_DISRUPTIVE),
    /** Data egress: chrome.screenshot returns an image of a screen in a prayer space; logs.tail leaks internals. */
    BOARD_COMMAND_INSPECT(Authority.BOARD_COMMAND_INSPECT),

    // ==================== Identity and audit ====================

    IAM_USER_READ(Authority.IAM_USER_READ),
    IAM_USER_WRITE(Authority.IAM_USER_WRITE),
    /** The escalation vector. ROOT only. */
    IAM_ROLE_GRANT(Authority.IAM_ROLE_GRANT),
    AUDIT_READ(Authority.AUDIT_READ);

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    @Override
    public String getAuthority() {
        return authority;
    }

    /**
     * Compile-time constants for {@code @PreAuthorize("hasAuthority(Permission.Authority.X)")}.
     * Mirrors the {@link Role.Authority} idiom.
     */
    public interface Authority {
        String MEDIA_UPLOAD_SELF = "media:upload:self";
        String MEDIA_UPLOAD_MODERATE = "media:upload:moderate";
        String MEDIA_UPLOAD_READ = "media:upload:read";
        String MEDIA_EVENT_WRITE = "media:event:write";

        String BOARD_CONTENT_READ = "board:content:read";
        String BOARD_POSTER_WRITE = "board:poster:write";
        String BOARD_EVENT_WRITE = "board:event:write";
        String BOARD_SOCIAL_WRITE = "board:social:write";
        String BOARD_WEEKLY_WRITE = "board:weekly:write";
        String BOARD_TICKER_WRITE = "board:ticker:write";

        String BOARD_CONFIG_READ = "board:config:read";
        String BOARD_CONFIG_WRITE = "board:config:write";
        String BOARD_REFRESH = "board:refresh";

        String BOARD_DEVICE_READ = "board:device:read";
        String BOARD_DEVICE_ENROLL = "board:device:enroll";
        String BOARD_DEVICE_REVOKE = "board:device:revoke";
        String BOARD_TELEMETRY_SUBSCRIBE = "board:telemetry:subscribe";

        String BOARD_COMMAND_BENIGN = "board:command:benign";
        String BOARD_COMMAND_DISRUPTIVE = "board:command:disruptive";
        String BOARD_COMMAND_INSPECT = "board:command:inspect";

        String IAM_USER_READ = "iam:user:read";
        String IAM_USER_WRITE = "iam:user:write";
        String IAM_ROLE_GRANT = "iam:role:grant";
        String AUDIT_READ = "audit:read";
    }
}
