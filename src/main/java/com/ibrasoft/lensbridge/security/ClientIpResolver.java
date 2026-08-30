package com.ibrasoft.lensbridge.security;

import com.ibrasoft.lensbridge.config.ClientIpProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Single source of truth for "which IP is this request really from".
 *
 * <p>{@code X-Forwarded-For} is a client-supplied header. nginx's {@code $proxy_add_x_forwarded_for}
 * <em>appends</em> the peer address to whatever the client already sent, so the left-hand entries are
 * entirely attacker-controlled and only the right-hand end is trustworthy. Reading
 * {@code split(",")[0]} therefore lets any caller mint a fresh identity per request, which defeats
 * every per-IP control built on top of it (rate limiting, lockout counters, audit trails).
 *
 * <p>This resolver instead:
 * <ol>
 *   <li>ignores the header outright unless the request's real TCP peer is a configured trusted
 *       proxy ({@code lensbridge.client-ip.trusted-proxies}), and</li>
 *   <li>counts <b>from the right</b>, skipping {@code lensbridge.client-ip.trusted-proxy-count}
 *       hops, so entries a client prepended are never selected, and</li>
 *   <li>falls back to {@link HttpServletRequest#getRemoteAddr()} whenever the header is absent,
 *       shorter than expected, or does not hold a syntactically valid IP literal.</li>
 * </ol>
 *
 * <p>Injected wherever a caller identity is needed; do not re-implement header parsing locally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** Used only when the container cannot report a peer address at all. */
    static final String UNKNOWN_IP = "unknown";

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    /** Longest possible textual IPv6 address (IPv4-mapped form), used as a cheap sanity bound. */
    private static final int MAX_IP_LITERAL_LENGTH = 45;

    private final ClientIpProperties properties;

    private List<IpAddressMatcher> trustedProxyMatchers = List.of();

    @PostConstruct
    void initialiseTrustedProxies() {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String cidr : properties.getTrustedProxies()) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(cidr.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "lensbridge.client-ip.trusted-proxies contains an invalid CIDR/IP: '" + cidr + "'", e);
            }
        }
        this.trustedProxyMatchers = List.copyOf(matchers);

        if (trustedProxyMatchers.isEmpty()) {
            log.info("Client IP resolution: no trusted proxies configured; {} will be ignored and the "
                    + "TCP peer address used instead.", FORWARDED_FOR_HEADER);
        } else {
            log.info("Client IP resolution: trusting {} for requests from {}, reading hop {} from the right.",
                    FORWARDED_FOR_HEADER, properties.getTrustedProxies(), properties.getTrustedProxyCount());
        }
    }

    /**
     * Resolves the originating client IP for {@code request}.
     *
     * @return a syntactically valid IP literal, or {@value #UNKNOWN_IP} if the container reported no
     *         peer address. Never {@code null}, so it is safe to use directly as a cache/lookup key.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return UNKNOWN_IP;
        }

        int hopsToSkip = properties.getTrustedProxyCount();
        if (hopsToSkip <= 0 || trustedProxyMatchers.isEmpty() || !isTrustedProxy(remoteAddr)) {
            // Either header parsing is disabled, or this request did not come through a proxy we
            // operate, so nothing it claims about its own origin can be believed.
            return remoteAddr;
        }

        String header = request.getHeader(FORWARDED_FOR_HEADER);
        if (header == null || header.isBlank()) {
            return remoteAddr;
        }

        List<String> hops = new ArrayList<>();
        for (String hop : header.split(",")) {
            String trimmed = hop.trim();
            if (!trimmed.isEmpty()) {
                hops.add(trimmed);
            }
        }

        int index = hops.size() - hopsToSkip;
        if (index < 0) {
            log.debug("{} has {} usable entries but {} trusted hops are configured; falling back to peer address.",
                    FORWARDED_FOR_HEADER, hops.size(), hopsToSkip);
            return remoteAddr;
        }

        String candidate = stripPort(hops.get(index));
        if (!isIpLiteral(candidate)) {
            log.debug("{} hop {} is not a valid IP literal; falling back to peer address.",
                    FORWARDED_FOR_HEADER, index);
            return remoteAddr;
        }
        return candidate;
    }

    private boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Address family mismatch or an unparseable peer address: simply not a match.
                log.trace("Trusted-proxy matcher rejected peer address '{}'", address);
            }
        }
        return false;
    }

    /**
     * Removes a {@code :port} suffix and IPv6 brackets. Proxies occasionally emit {@code 1.2.3.4:5678}
     * or {@code [2001:db8::1]:443}; a bare IPv6 address (which is full of colons) is left untouched.
     */
    private static String stripPort(String value) {
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            return close > 0 ? value.substring(1, close) : value;
        }
        int colon = value.indexOf(':');
        if (colon > 0 && value.indexOf(':', colon + 1) < 0) {
            return value.substring(0, colon);
        }
        return value;
    }

    /**
     * Lexical-only IP check. Deliberately avoids {@code InetAddress.getByName}, which would perform a
     * DNS lookup for anything that is not an IP literal — an attacker-triggered outbound request.
     */
    private static boolean isIpLiteral(String value) {
        if (value.isEmpty() || value.length() > MAX_IP_LITERAL_LENGTH) {
            return false;
        }
        if (value.indexOf(':') < 0) {
            return IPV4.matcher(value).matches();
        }
        // IPv6: hex groups separated by colons, optionally ending in a dotted-quad tail.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.digit(c, 16) < 0 && c != ':' && c != '.') {
                return false;
            }
        }
        return true;
    }
}
