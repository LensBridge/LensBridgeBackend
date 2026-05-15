package com.ibrasoft.lensbridge.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {
    private IpUtils() {}

    /**
     * Resolves the originating client IP for a request.
     *
     * <p>Prefers the first entry of the {@code X-Forwarded-For} header when that
     * header is present and non-blank and its first segment is non-blank;
     * otherwise falls back to {@link HttpServletRequest#getRemoteAddr()}.
     */
    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String firstIp = xForwardedFor.split(",")[0].trim();
            if (!firstIp.isBlank()) {
                return firstIp;
            }
        }
        return request.getRemoteAddr();
    }
}
