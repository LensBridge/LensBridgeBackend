package com.ibrasoft.lensbridge.config.stomp;

import com.ibrasoft.lensbridge.model.auth.Permission;
import com.ibrasoft.lensbridge.model.auth.Role;
import com.ibrasoft.lensbridge.security.jwt.JwtUtils;
import com.ibrasoft.lensbridge.security.services.AuthenticatedUser;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The device topics carry command output, which for chrome.screenshot is an image of a
 * display in a prayer space. A valid JWT alone must not be enough to read them.
 */
class StompJwtChannelInterceptorTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
    private final StompJwtChannelInterceptor interceptor =
            new StompJwtChannelInterceptor(jwtUtils, userDetailsService);
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> subscribeTo(String destination, Permission... permissions) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        List<GrantedAuthority> authorities = java.util.Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.getAuthority()))
                .toList();
        accessor.setUser(new UsernamePasswordAuthenticationToken("u@example.com", null, authorities));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribeAnonymously(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribeToDeviceTopicRequiresTelemetryPermission() {
        Message<byte[]> message = subscribeTo("/topic/devices/abc/commands");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(Permission.BOARD_TELEMETRY_SUBSCRIBE.getAuthority());
    }

    /** The regression this check exists for: a plain uploader must not read command output. */
    @Test
    void plainUserCannotSubscribeToCommandOutput() {
        Message<byte[]> message = subscribeTo("/topic/devices/abc/commands",
                Role.USER.getPermissions().toArray(new Permission[0]));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeIsAllowedWithTelemetryPermission() {
        Message<byte[]> message = subscribeTo("/topic/devices/abc/commands",
                Permission.BOARD_TELEMETRY_SUBSCRIBE);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void boardViewerCanSubscribe() {
        Message<byte[]> message = subscribeTo("/topic/devices",
                Role.BOARD_VIEWER.getPermissions().toArray(new Permission[0]));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribeWithoutAPrincipalIsRejected() {
        Message<byte[]> message = subscribeAnonymously("/topic/devices/abc");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void subscribeToAnUnrelatedLiteralDestinationIsNotGated() {
        Message<byte[]> message = subscribeAnonymously("/topic/something-else");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    /**
     * The SimpleBroker matches a subscription destination against published destinations
     * with an AntPathMatcher, so a subscription is a <em>pattern</em>. A prefix test on the
     * raw string is therefore not a gate: /topic/** does not start with /topic/devices but
     * still receives every message published there, screenshots included.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/topic/**",
            "/topic/*/**",
            "/topic/*",
            "/topic/devices*",
            "/topic/device?/**",
            "/topic/{anything}/commands",
            "/**",
    })
    void wildcardSubscriptionsCannotBeUsedToReachDeviceTopics(String destination) {
        Message<byte[]> message = subscribeTo(destination);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Even holding the permission, a pattern subscription is refused rather than authorized. */
    @Test
    void wildcardSubscriptionsAreRefusedEvenWithTelemetryPermission() {
        Message<byte[]> message = subscribeTo("/topic/**", Permission.BOARD_TELEMETRY_SUBSCRIBE);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Pattern subscriptions are not permitted");
    }

    // ==================== CONNECT, unchanged behaviour ====================

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtUtils.validateJwtToken("bad")).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT");
    }

    @Test
    void connectWithValidTokenAttachesThePrincipal() {
        // AuthenticatedUser, not the plain Spring principal: the interceptor now compares the
        // token's generation against the account's, and AuthenticatedUser.tokenVersionOf fails
        // closed at -1 for any principal that cannot report one.
        UserDetails user = new AuthenticatedUser(
                "u@example.com",
                "x",
                true,
                List.of(new SimpleGrantedAuthority(Permission.BOARD_TELEMETRY_SUBSCRIBE.getAuthority())),
                0L);
        when(jwtUtils.validateJwtToken("good")).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken("good")).thenReturn("u@example.com");
        when(userDetailsService.loadUserByUsername("u@example.com")).thenReturn(user);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer good");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        StompHeaderAccessor result = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertThat(result).isNotNull();
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getName()).isEqualTo("u@example.com");
    }
}
