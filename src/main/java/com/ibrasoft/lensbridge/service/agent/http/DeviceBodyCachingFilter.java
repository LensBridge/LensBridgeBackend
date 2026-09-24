package com.ibrasoft.lensbridge.service.agent.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the body of every {@code /api/agent/} request into memory so the device signature can
 * be checked over the exact bytes and Spring can still bind the body afterwards (see
 * {@link CachedBodyRequest}). Agent request bodies are small JSON (the largest, a content
 * bundle request listing 2000 media hashes, is about 140 KB), so anything over
 * {@link #MAX_BODY_BYTES} is refused outright rather than buffered.
 */
@Component
public class DeviceBodyCachingFilter extends OncePerRequestFilter {

    static final String AGENT_PATH_PREFIX = "/api/agent/";
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /**
     * Matched on the path inside the application, exactly as the interceptor's
     * {@code /api/agent/**} pattern is, so a context path or a proxy prefix
     * ({@code X-Forwarded-Prefix}) cannot make the two disagree.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATHS.getPathWithinApplication(request).startsWith(AGENT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (body.length > MAX_BODY_BYTES) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Request body is larger than 1 MiB\"}");
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }
}
