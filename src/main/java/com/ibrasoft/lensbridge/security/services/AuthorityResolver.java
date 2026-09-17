package com.ibrasoft.lensbridge.security.services;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Expands a {@link User} into the authority set Spring Security checks against:
 * <pre>
 *   authorities = roles (as ROLE_*) ∪ ⋃(role bundles) ∪ direct grants
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class AuthorityResolver {

    private final UserRepository userRepository;

    public Collection<GrantedAuthority> resolveAuthorities(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>(resolvePermissions(user));
        authorities.addAll(user.getRoles());
        return authorities;
    }

    public Set<Permission> resolvePermissions(User user) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (Role role : user.getRoles()) {
            permissions.addAll(role.getPermissions());
        }
        permissions.addAll(directGrants(user.getId()));
        return permissions;
    }

    public Set<Permission> resolvePermissions(UUID userId) {
        return userRepository.findById(userId)
                .map(this::resolvePermissions)
                .orElseGet(() -> EnumSet.noneOf(Permission.class));
    }

    private Set<Permission> directGrants(UUID userId) {
        if (userId == null) {
            return Set.of(); // unsaved entity, e.g. in tests
        }
        return userRepository.findDirectPermissions(userId);
    }
}
