package com.ibrasoft.lensbridge.model.auth;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    USER, ADMIN, ROOT;

    // String constants for @PreAuthorize("hasRole('" + Role.Authority.ADMIN + "')")
    // Spring Security's hasRole() prepends ROLE_ automatically, so these are just the name.
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
