package com.ibrasoft.lensbridge.config.stomp;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates STOMP {@code CONNECT} and authorizes STOMP {@code SUBSCRIBE}.
 * <p>
 * <b>CONNECT:</b> validates the JWT carried in the {@code Authorization} native header.
 * Browsers cannot set arbitrary headers on the WebSocket HTTP upgrade, so we authenticate
 * at the STOMP layer rather than the HTTP handshake. Subsequent frames inherit the resolved
 * principal via {@link StompHeaderAccessor#setUser(Principal)}.
 * <p>
 * <b>SUBSCRIBE:</b> a valid JWT is not enough to read the device topics. Command results
 * are published to {@code /topic/devices/{id}/commands} including the agent's raw output,
 * which for {@code chrome.screenshot} is an image of a display and for {@code logs.tail} is
 * agent internals. Reading those requires {@link Permission#BOARD_TELEMETRY_SUBSCRIBE}.
 * Without this check any verified user — including a student who signed up only to upload a
 * photo — could subscribe and receive them.
 * <p>
 * Pattern destinations are refused outright, before any permission is considered. The
 * broker treats a subscription destination as an Ant pattern, so {@code /topic/**} would
 * otherwise pass a prefix test while still receiving every device message.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    /**
     * The only destination prefix the backend publishes to today (see DeviceEventPublisher).
     * A new topic family must make its own authorization decision here.
     */
    private static final String DEVICE_TOPIC_PREFIX = "/topic/devices";

    /**
     * AntPathMatcher metacharacters. {@code DefaultSubscriptionRegistry} matches a
     * subscription destination against published destinations as a <em>pattern</em>, so any
     * of these turns a SUBSCRIBE into a wildcard that a prefix test cannot reason about:
     * {@code /topic/**} does not start with {@code /topic/devices} yet receives everything
     * published there. Nothing in this application subscribes by pattern, so they are
     * refused outright rather than pattern-matched against a policy.
     */
    private static final char[] PATTERN_CHARS = {'*', '?', '{'};

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @SuppressWarnings("null") // accessor is null-checked prior to switch statement
    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            default -> { /* other frames inherit the principal resolved at CONNECT */ }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        String token = stripBearer(header);
        if (token == null) {
            log.warn("STOMP CONNECT rejected: missing Authorization header");
            throw new IllegalArgumentException("Missing Authorization header on STOMP CONNECT");
        }
        if (!jwtUtils.validateJwtToken(token)) {
            log.warn("STOMP CONNECT rejected: invalid JWT");
            throw new IllegalArgumentException("Invalid JWT on STOMP CONNECT");
        }

        String username = jwtUtils.getUserNameFromJwtToken(token);
        UserDetails user = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        accessor.setUser(auth);
        log.debug("STOMP authenticated as {}", username);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        // Checked before the prefix test, and regardless of what the subscriber holds: a
        // pattern is not a destination this gate can authorize, so there is no version of it
        // that is allowed through.
        if (isPattern(destination)) {
            log.warn("STOMP SUBSCRIBE rejected: pattern destination {}", destination);
            throw new AccessDeniedException(
                    "Pattern subscriptions are not permitted; subscribe to an exact destination");
        }

        if (!destination.startsWith(DEVICE_TOPIC_PREFIX)) {
            return;
        }

        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication auth) || !auth.isAuthenticated()) {
            log.warn("STOMP SUBSCRIBE to {} rejected: no authenticated principal", destination);
            throw new AccessDeniedException("Not authenticated");
        }
        if (!hasAuthority(auth, Permission.BOARD_TELEMETRY_SUBSCRIBE)) {
            log.warn("STOMP SUBSCRIBE to {} rejected for {}: missing {}",
                    destination, auth.getName(), Permission.BOARD_TELEMETRY_SUBSCRIBE.getAuthority());
            throw new AccessDeniedException("Missing permission "
                    + Permission.BOARD_TELEMETRY_SUBSCRIBE.getAuthority());
        }
        log.debug("STOMP SUBSCRIBE to {} authorized for {}", destination, auth.getName());
    }

    private static boolean isPattern(String destination) {
        for (char c : PATTERN_CHARS) {
            if (destination.indexOf(c) >= 0) return true;
        }
        return false;
    }

    private static boolean hasAuthority(Authentication auth, Permission permission) {
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (permission.getAuthority().equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static String stripBearer(String header) {
        if (header == null) return null;
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return header.trim();
    }
}
