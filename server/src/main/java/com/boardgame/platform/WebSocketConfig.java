package com.boardgame.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeSocketHandler realtimeSocketHandler;

    WebSocketConfig(RealtimeSocketHandler realtimeSocketHandler) {
        this.realtimeSocketHandler = realtimeSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeSocketHandler, "/ws/realtime")
                .setAllowedOriginPatterns("*");
    }
}
