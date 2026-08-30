package com.ibrasoft.lensbridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls how {@link com.ibrasoft.lensbridge.security.ClientIpResolver} derives the originating
 * client IP from an inbound request.
 *
 * <p>Defaults are deliberately <b>fail-safe</b>: with {@link #trustedProxies} empty, no
 * {@code X-Forwarded-For} header is honoured at all and every caller is identified by its real TCP
 * peer address. A deployment that actually sits behind a reverse proxy has to opt in by listing the
 * proxy's address range, which means a forgotten configuration degrades to over-counting (one shared
 * bucket) rather than to a trivially spoofable rate-limit key.
 */
@Component
@ConfigurationProperties(prefix = "lensbridge.client-ip")
@Data
public class ClientIpProperties {

    /**
     * CIDR blocks (or bare IP literals) of reverse proxies that are allowed to supply
     * {@code X-Forwarded-For}. Requests arriving from any other peer have the header ignored.
     * Empty (the default) means no proxy is trusted.
     */
    private List<String> trustedProxies = new ArrayList<>();

    /**
     * Number of reverse proxies we operate between the public internet and this application.
     * The client IP is read that many entries from the right-hand end of {@code X-Forwarded-For},
     * so hops a client prepends itself are skipped. {@code 1} matches the single nginx in
     * {@code docker/nginx/nginx.conf}. {@code 0} disables header parsing entirely.
     */
    private int trustedProxyCount = 1;
}
