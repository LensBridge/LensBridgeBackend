package com.ibrasoft.lensbridge.security;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.tcketmanagebackend.security.TcketManageAuthorizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Maps tcketmanage-core's three capabilities onto this application's permissions.
 * <p>
 * Core asks "may this caller scan / manage events / administer", never "does this caller have
 * role X", precisely so a host that models authorization as permissions can answer in its own
 * terms. Without this bean core falls back to its role-name default, which checks for roles
 * named {@code SCANNER} and {@code EVENT_MANAGER} — roles this codebase does not have and will
 * not have, so every operator endpoint would 403 no matter who called it.
 * <p>
 * Core discovers this by type, so the bean name is free; contrast the {@code @commandAuthorizer}
 * expressions in this package, which are bound to their bean name.
 * <p>
 * The permissions are deliberately their own {@code tcket:*} namespace rather than a reuse of
 * {@code media:event:write} or {@code board:event:write}. Those name a media event and a
 * MusallahBoard calendar event; neither is a ticketed event, and mapping onto either would grant
 * ticket issuance and payment settlement to every media moderator and board editor.
 */
@Component
public class LensBridgeTcketAuthorizer implements TcketManageAuthorizer {

    @Override
    public boolean canScan() {
        return has(Permission.TCKET_SCAN);
    }

    @Override
    public boolean canManageEvents() {
        return has(Permission.TCKET_MANAGE);
    }

    @Override
    public boolean canAdminister() {
        return has(Permission.TCKET_ADMIN);
    }

    private static boolean has(Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (permission.getAuthority().equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
