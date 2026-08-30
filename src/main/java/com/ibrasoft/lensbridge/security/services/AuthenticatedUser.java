package com.ibrasoft.lensbridge.security.services;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The Spring Security principal, extended with the account's current token generation.
 * <p>
 * {@code AuthTokenFilter} has to compare a JWT's {@code tv} claim against the value stored on
 * the account, and it already loads the account on every request to re-resolve authorities.
 * Carrying the counter on the principal means that comparison costs no second query. Anything
 * that only cares about the username, password or authorities keeps working — this is still a
 * plain {@link org.springframework.security.core.userdetails.User}.
 */
public class AuthenticatedUser extends org.springframework.security.core.userdetails.User {

    private final long tokenVersion;

    public AuthenticatedUser(String username,
                             String password,
                             boolean enabled,
                             Collection<? extends GrantedAuthority> authorities,
                             long tokenVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    /**
     * The generation recorded for {@code details}, or {@code -1} when it is not one of ours.
     * <p>
     * No account's counter can hold {@code -1}, so an unknown principal type fails the
     * comparison rather than silently passing it.
     */
    public static long tokenVersionOf(UserDetails details) {
        return details instanceof AuthenticatedUser user ? user.getTokenVersion() : -1L;
    }
}
