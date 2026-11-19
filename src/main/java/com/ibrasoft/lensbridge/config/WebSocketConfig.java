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

    /**
     * Largest text frame a socket may carry: 10 MiB. The container's default is 8 KB, and a
     * frame over it is closed with 1009 (message too big), which is exactly what a
     * {@code chrome.screenshot} or {@code logs.tail} result looks like. The agent caps a
     * screenshot's base64 at 8 MiB (internal/commands/chrome_screenshot.go, which cites this
     * figure), leaving the headroom for the JSON envelope around it. Change the two together.
     * <p>
     * On by default: it used to require {@code lensbridge.websocket.container-customizer.enabled=true},
     * which no shipped configuration set, so a stock deployment closed every large result with
     * 1009. Setting the property to {@code false} still turns it off. Only text is raised:
     * nothing here accepts binary frames. The cost is that an unauthenticated socket may also
     * send a frame this large, which is why {@code AgentSessionSweeper} gives such sockets a
     * short deadline.
     */
    static final int MAX_TEXT_MESSAGE_BYTES = 10 * 1024 * 1024;

    @Bean
    @ConditionalOnProperty(name = "lensbridge.websocket.container-customizer.enabled",
            havingValue = "true", matchIfMissing = true)
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BYTES);
        return container;
    }
}
