package com.ibrasoft.lensbridge.security;

import com.ibrasoft.lensbridge.dto.board.request.IssueCommandRequest;
import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.minbar.board.commands.CommandKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Authorizes a device command by its {@code kind}, for use from
 * {@code @PreAuthorize("@commandAuthorizer.canIssue(authentication, #request)")}.
 * <p>
 * The required permission depends on the request body, which a plain {@code hasAuthority}
 * expression cannot see. Method security runs after argument resolution, so a bean call in
 * the expression can — and this keeps the rule declarative at the endpoint instead of
 * buried in the dispatcher.
 */
@Component
@Slf4j
public class CommandAuthorizer {

    public boolean canIssue(Authentication authentication, IssueCommandRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || request == null) {
            return false;
        }

        // An unrecognised kind is deliberately allowed through: CommandDispatcher rejects it
        // with 400, which is a more useful answer to a typo than 403. Nothing is issued either way.
        CommandKind kind = CommandKind.from(request.kind()).orElse(null);
        if (kind == null) {
            return true;
        }

        Permission required = kind.getRequiredPermission();
        boolean allowed = hasAuthority(authentication, required);
        if (!allowed) {
            log.warn("Denied command {} for {}: missing {}",
                    kind.getWireName(), authentication.getName(), required.getAuthority());
        }
        return allowed;
    }

    private static boolean hasAuthority(Authentication authentication, Permission permission) {
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (permission.getAuthority().equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
