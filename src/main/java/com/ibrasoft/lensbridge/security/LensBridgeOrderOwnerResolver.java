package com.ibrasoft.lensbridge.security;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.service.UserService;
import com.ibrasoft.tcketmanagebackend.service.order.OrderOwnerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ties a tCketManage order to the LensBridge account that placed it.
 */
@Component
@RequiredArgsConstructor
public class LensBridgeOrderOwnerResolver implements OrderOwnerResolver {

    private final UserService userService;

    @Override
    public String currentOwnerRef() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return userService.findByEmail(authentication.getName())
                .map(User::getId)
                .map(Object::toString)
                .orElse(null);
    }
}
