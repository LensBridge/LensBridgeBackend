package com.ibrasoft.lensbridge.dto.auth.response;

import com.ibrasoft.lensbridge.model.auth.Permission;

/**
 * One permission, in both the form the API accepts ({@code name}, the enum constant) and
 * the form that appears in tokens and annotations ({@code authority}).
 * <p>
 * {@code domain} is the namespace segment {@code board}, {@code media}, {@code iam},
 * {@code audit} which is enough for the console to group the picker without hardcoding
 * a list of groups that would then drift.
 */
public record PermissionResponse(
        String name,
        String authority,
        String domain
) {
    public static PermissionResponse of(Permission permission) {
        String authority = permission.getAuthority();
        int firstColon = authority.indexOf(':');
        String domain = firstColon > 0 ? authority.substring(0, firstColon) : authority;
        return new PermissionResponse(permission.name(), authority, domain);
    }
}
