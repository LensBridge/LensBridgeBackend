package com.ibrasoft.lensbridge.model.auth;

/**
 * Enum representing user roles in the system.
 * This enum provides type safety and prevents typos in role names.
 */
public enum Role {
    ROLE_USER("ROLE_USER"),
    ROLE_MODERATOR("ROLE_MODERATOR"), 
    ROLE_ADMIN("ROLE_ADMIN"),
    ROLE_VERIFIED("ROLE_VERIFIED");

    // Static constants for use in @PreAuthorize annotations
    public static final String USER = "ROLE_USER";
    public static final String MODERATOR = "ROLE_MODERATOR";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String VERIFIED = "ROLE_VERIFIED";

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }

    /**
     * Gets the authority string used by Spring Security.
     * @return the authority string (e.g., "ROLE_USER")
     */
    public String getAuthority() {
        return authority;
    }

    /**
     * Gets the role name without the ROLE_ prefix.
     * @return the role name (e.g., "USER")
     */
    public String getRoleName() {
        return authority.substring(5); // Remove "ROLE_" prefix
    }

    /**
     * Converts a string to a Role enum.
     * @param authority the authority string
     * @return the corresponding Role enum
     * @throws IllegalArgumentException if the authority is not recognized
     */
    public static Role fromAuthority(String authority) {
        for (Role role : values()) {
            if (role.authority.equals(authority)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role authority: " + authority);
    }

    @Override
    public String toString() {
        return authority;
    }
}
