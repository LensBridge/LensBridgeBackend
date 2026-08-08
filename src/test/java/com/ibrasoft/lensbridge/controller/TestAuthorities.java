package com.ibrasoft.lensbridge.controller;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the authority set a real request would carry, so permission tests exercise the
 * production shape rather than a convenient approximation.
 * <p>
 * Mirrors {@code AuthorityResolver}: a role contributes both its {@code ROLE_*} authority
 * and every permission it confers. {@code @WithMockUser(roles = "ADMIN")} grants only the
 * former, which would make any {@code hasAuthority} check look broken.
 */
final class TestAuthorities {

    private TestAuthorities() {
    }

    /** Authenticates as a principal holding these roles, fully expanded. */
    static RequestPostProcessor as(Role... roles) {
        return SecurityMockMvcRequestPostProcessors
                .user("u@example.com")
                .authorities(expand(roles));
    }

    /** Authenticates as a principal holding exactly these permissions and no role at all. */
    static RequestPostProcessor asHolderOf(Permission... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Permission permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission.getAuthority()));
        }
        return SecurityMockMvcRequestPostProcessors
                .user("u@example.com")
                .authorities(authorities.toArray(new GrantedAuthority[0]));
    }

    private static GrantedAuthority[] expand(Role... roles) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(role.getAuthority()));
            for (Permission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getAuthority()));
            }
        }
        return authorities.toArray(new GrantedAuthority[0]);
    }
}
