package com.ibrasoft.lensbridge.dto.auth.response;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;

import java.util.List;

/**
 * A role plus what it actually confers, so the admin console can show an
 * operator what
 * they are about to hand someone instead of an opaque enum name.
 */
public record RoleDefinitionResponse(
                String name,
                String description,
                List<String> permissions) {
        public static RoleDefinitionResponse of(Role role) {
                return new RoleDefinitionResponse(
                                role.name(),
                                role.getDescription(),
                                role.getPermissions().stream()
                                                .map(Permission::getAuthority)
                                                .sorted()
                                                .toList());
        }
}
