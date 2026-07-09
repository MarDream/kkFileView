package cn.keking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 消息代理配置
 * 仅在 collaboration.edit.enabled=true 时启用
 *
 * @author Claude Code
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "collaboration.edit.enabled", havingValue = "true")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 内置消息代理，客户端订阅 /topic 开头的目的地
        // 注：不配置 heartbeat，否则需额外提供 TaskScheduler bean
        config.enableSimpleBroker("/topic");
        // 客户端发送消息到 /app 开头的目的地
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP 端点，使用 SockJS 作为回退
        registry.addEndpoint("/ws-collab")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}