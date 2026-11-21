package com.ibrasoft.lensbridge.repository.auth;

import java.util.UUID;

import com.ibrasoft.lensbridge.model.auth.Permission;

/** One direct permission grant and the user holding it; see {@link UserRepository#findDirectPermissionsForUsers}. */
public record DirectPermissionRow(UUID userId, Permission permission) {
}
