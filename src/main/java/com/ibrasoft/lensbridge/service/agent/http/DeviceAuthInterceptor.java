package com.ibrasoft.lensbridge.service.agent.http;

import com.ibrasoft.lensbridge.model.board.Device;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.Arrays;

/**
 * Authenticates device requests before Spring resolves any controller argument, for every
 * endpoint with an {@link AuthenticatedDevice} parameter. Doing it here rather than in the
 * argument resolver means the check can never depend on parameter order: an unauthenticated
 * request gets a 401, never a 400 that reveals something about the body format.
 */
@Component
@RequiredArgsConstructor
public class DeviceAuthInterceptor implements HandlerInterceptor {

    /** Request attribute holding the authenticated {@link Device}. */
    static final String DEVICE_ATTRIBUTE = DeviceAuthInterceptor.class.getName() + ".device";

    private static final byte[] NO_BODY = new byte[0];

    /**
     * Looked up on first use rather than injected: every web context loads this interceptor
     * (through WebConfig), including test slices that have no device endpoints and no
     * repositories to build an authenticator from.
     */
    private final ObjectProvider<DeviceRequestAuthenticator> authenticator;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && requiresDevice(method)) {
            Device device = authenticator.getObject().authenticate(request, bodyOf(request));
            request.setAttribute(DEVICE_ATTRIBUTE, device);
        }
        return true;
    }

    static boolean requiresDevice(HandlerMethod method) {
        return Arrays.stream(method.getMethodParameters())
                .anyMatch(p -> p.hasParameterAnnotation(AuthenticatedDevice.class));
    }

    /**
     * The body the signature covers. Every {@code /api/agent/} request passes through
     * {@link DeviceBodyCachingFilter}; a device endpoint anywhere else would have no cached
     * body, and that is a programming error worth failing loudly on rather than silently
     * signing over nothing.
     */
    private static byte[] bodyOf(HttpServletRequest request) {
        CachedBodyRequest cached = WebUtils.getNativeRequest(request, CachedBodyRequest.class);
        if (cached == null) {
            throw new IllegalStateException("Device-authenticated endpoint " + request.getRequestURI()
                    + " is outside " + DeviceBodyCachingFilter.AGENT_PATH_PREFIX + ", so its body was not cached");
        }
        return cached.body().length == 0 ? NO_BODY : cached.body();
    }
}
