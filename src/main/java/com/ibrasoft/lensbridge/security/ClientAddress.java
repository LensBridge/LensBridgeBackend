package com.ibrasoft.lensbridge.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The single place that decides which address counts as "the client" for rate limiting and
 * logging.
 *
 * <p>This is deliberately just {@code getRemoteAddr()}. With
 * {@code server.forward-headers-strategy=native}, Tomcat's RemoteIpValve has already replaced it
 * with the client address from X-Forwarded-For, and only when the connection came from a trusted
 * proxy ({@code server.tomcat.remoteip.internal-proxies}); anyone else keeps their socket
 * address. Reading the header by hand would take the leftmost entry, which the client itself
 * controls, so any caller could pick a fresh rate-limit bucket per request. The
 * {@code framework} strategy has the same flaw: Spring's ForwardedHeaderFilter believes the
 * header from any peer. {@code ClientAddressProxyIntegrationTest} pins this down.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
