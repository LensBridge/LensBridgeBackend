package com.ibrasoft.lensbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ibrasoft.lensbridge.handler.AgentWebSocketHandler;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BoardStreamHandler boardStreamHandler;
    private final AgentWebSocketHandler agentWebSocketHandler;

    @Value("${frontend.baseurl}")
    String frontendBaseUrl;

    @Value("${musallahboard.baseurl}")
    String musallahBoardBaseUrl;

    public WebSocketConfig(BoardStreamHandler boardStreamHandler,
                           AgentWebSocketHandler agentWebSocketHandler) {
        this.boardStreamHandler = boardStreamHandler;
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Path is the deployed kiosk's; boards scope themselves with ?deviceId=<uuid>.
        registry.addHandler(boardStreamHandler, "/api/refresh-musallahboard")
                .setAllowedOrigins(frontendBaseUrl, musallahBoardBaseUrl);

        // Agents authenticate per-frame inside the channel; the WS upgrade itself is open.
        registry.addHandler(agentWebSocketHandler, "/api/agent/ws")
                .setAllowedOrigins("*");
    }

    @Bean
    @ConditionalOnProperty(name = "lensbridge.websocket.container-customizer.enabled", havingValue = "true")
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container =
                new ServletServerContainerFactoryBean();

        container.setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10 MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);

        return container;
    }
}
