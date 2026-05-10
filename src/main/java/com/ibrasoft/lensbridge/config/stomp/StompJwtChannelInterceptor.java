package com.ibrasoft.lensbridge.config.stomp;

import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Validates the JWT carried in the STOMP {@code CONNECT} frame's {@code Authorization}
 * native header. Subsequent SUBSCRIBE / SEND frames inherit the resolved principal via
 * {@link StompHeaderAccessor#setUser(java.security.Principal)}.
 * <p>
 * Browsers cannot set arbitrary headers on the WebSocket HTTP upgrade, so we authenticate
 * at the STOMP layer rather than the HTTP handshake.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

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
        return message;
    }

    private static String stripBearer(String header) {
        if (header == null) return null;
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return header.trim();
    }
}
