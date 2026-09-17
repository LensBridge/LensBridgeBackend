package com.ibrasoft.lensbridge.config.stomp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP-over-WebSocket endpoint at {@code /api/dashboard/ws} for the admin dashboard.
 * <p>
 * Topics published by the backend:
 * <ul>
 *   <li>{@code /topic/devices/{id}} — heartbeat + online/offline transitions</li>
 *   <li>{@code /topic/devices/{id}/commands/{cmdId}} — per-command lifecycle events</li>
 *   <li>{@code /topic/devices/{id}/commands} — fan-out of all command events for a device</li>
 * </ul>
 * Authentication is enforced by {@link StompJwtChannelInterceptor} on the
 * {@code clientInboundChannel} — every STOMP {@code CONNECT} must carry a valid JWT.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtChannelInterceptor jwtInterceptor;

    @Value("${frontend.baseurl}")
    private String frontendBaseUrl;

    @Value("${musallahboard.baseurl}")
    private String musallahBoardBaseUrl;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/dashboard/ws")
                .setAllowedOrigins(frontendBaseUrl, musallahBoardBaseUrl);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(10 * 1024 * 1024);      // inbound message size
        registry.setSendBufferSizeLimit(10 * 1024 * 1024);   // outbound buffer
        registry.setSendTimeLimit(20_000);                   // optional
    }
}
