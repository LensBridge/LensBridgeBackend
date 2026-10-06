package com.ibrasoft.lensbridge.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The single place that decides which address counts as "the client" for rate limiting and
 * logging.
 *
 * <p>This is deliberately just {@code getRemoteAddr()}. Proxy headers such as X-Forwarded-For
 * are resolved by Spring ({@code server.forward-headers-strategy}), which only honours them
 * from trusted proxies and then reports the real client through {@code getRemoteAddr()}. Reading
 * the header by hand would take the leftmost entry, which the client itself controls, so any
 * caller could pick a fresh rate-limit bucket per request.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
