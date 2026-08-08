package com.ibrasoft.lensbridge.model.auth;

import java.util.Set;

import org.springframework.security.core.GrantedAuthority;

/**
 * A named bundle of {@link Permission}s. Roles are what you assign to a person, because
 * "make them a board editor" is how humans think — but nothing in this codebase branches
 * on a role name. Every {@code @PreAuthorize} checks a permission.
 * <p>
 * The bundles themselves are in {@link RolePermissions}.
 * <p>
 * Role authorities are still emitted as {@code ROLE_*} alongside the expanded permission
 * set, so any surviving {@code hasRole(...)} check keeps working.
 */
public enum Role implements GrantedAuthority {

    // ---------- Legacy media sharing ----------
    USER("Upload and manage your own media submissions"),
    ADMIN("Moderate media submissions and read the audit log"),

    // ---------- MusallahBoard ----------
    BOARD_VIEWER("View board content, config, and device status; change nothing"),
    BOARD_EDITOR("Create and edit everything that appears on a board"),
    BOARD_ADMIN("Full control of boards and devices, including enrollment and remote commands"),

    // ---------- Everything ----------
    ROOT("Unrestricted, including granting roles to other users");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** The permissions this role confers. Never null; may not be modified. */
    public Set<Permission> getPermissions() {
        return RolePermissions.of(this);
    }

    // String constants for @PreAuthorize("hasRole('" + Role.Authority.ADMIN + "')")
    // Spring Security's hasRole() prepends ROLE_ automatically, so these are just the name.
    // These will be removed once media-related 
    // features are stripped from the codebase - they are here purely for back-compat until I fully 
    // transition over to Minbar
    @Deprecated
    public interface Authority {
        String USER = "USER";
        String ADMIN = "ADMIN";
        String ROOT = "ROOT";
    }

    @Override
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
