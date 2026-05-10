package com.ibrasoft.lensbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ibrasoft.lensbridge.handler.AgentWebSocketHandler;
import com.ibrasoft.lensbridge.handler.SignboardHandler;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignboardHandler signboardHandler;
    private final AgentWebSocketHandler agentWebSocketHandler;

    @Value("${frontend.baseurl}")
    String frontendBaseUrl;

    @Value("${musallahboard.baseurl}")
    String musallahBoardBaseUrl;

    public WebSocketConfig(SignboardHandler signboardHandler,
                           AgentWebSocketHandler agentWebSocketHandler) {
        this.signboardHandler = signboardHandler;
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signboardHandler, "/api/refresh-musallahboard")
                .setAllowedOrigins(frontendBaseUrl, musallahBoardBaseUrl);

        // Agents authenticate per-frame inside the channel; the WS upgrade itself is open.
        registry.addHandler(agentWebSocketHandler, "/api/agent/ws")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container =
                new ServletServerContainerFactoryBean();

        container.setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10 MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);

        return container;
    }
}