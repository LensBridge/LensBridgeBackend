package com.ibrasoft.lensbridge.config;

import com.ibrasoft.lensbridge.handler.AgentWebSocketHandler;
import com.ibrasoft.lensbridge.handler.BoardStreamHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketConfigTest {

    private final WebSocketConfig config =
            new WebSocketConfig(mock(BoardStreamHandler.class), mock(AgentWebSocketHandler.class));

    /**
     * Regression: the bean that lifts Tomcat's 8 KB text buffer was gated on a property no
     * configuration sets, so chrome.screenshot and logs.tail results (far larger than 8 KB)
     * closed the socket with 1009. It has to be on unless someone opts out.
     */
    @Test
    void containerCustomizerIsOnWhenThePropertyIsAbsent() throws Exception {
        ConditionalOnProperty gate = WebSocketConfig.class.getMethod("webSocketContainer")
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(gate.matchIfMissing()).isTrue();
        assertThat(gate.havingValue()).isEqualTo("true");
    }

    /** The agent caps a screenshot at 8 MiB of base64; the envelope needs room on top. */
    @Test
    void textBufferFitsTheLargestFrameTheAgentSends() {
        ServletServerContainerFactoryBean container = config.webSocketContainer();

        int eightMibOfBase64 = 8 << 20;
        assertThat(container.getMaxTextMessageBufferSize()).isEqualTo(WebSocketConfig.MAX_TEXT_MESSAGE_BYTES);
        assertThat(container.getMaxTextMessageBufferSize()).isGreaterThan(eightMibOfBase64 + 64 * 1024);
    }
}
