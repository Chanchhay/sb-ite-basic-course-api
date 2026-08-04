package kh.edu.istad.ite.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/customer-display")
                .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws/customer-display-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws/notifications-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        registry.enableSimpleBroker("/topic", "/queue");

        registry.setUserDestinationPrefix("/user");
    }
}
