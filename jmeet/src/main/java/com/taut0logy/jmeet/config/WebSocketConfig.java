package com.taut0logy.jmeet.config;

import java.util.List;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** §12.2: STOMP broker relay against RabbitMQ's STOMP plugin, not the built-in simple broker. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RabbitConnectionDetails rabbitConnectionDetails;
    private final RealtimeProperties realtimeProperties;
    private final ClientProperties clientProperties;

    public WebSocketConfig(RabbitConnectionDetails rabbitConnectionDetails, RealtimeProperties realtimeProperties,
            ClientProperties clientProperties) {
        this.rabbitConnectionDetails = rabbitConnectionDetails;
        this.realtimeProperties = realtimeProperties;
        this.clientProperties = clientProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(clientProperties.baseUrl());
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        converters.add(new JacksonJsonMessageConverter());
        return false;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setVirtualHost("/")
                .setRelayHost(rabbitConnectionDetails.getFirstAddress().host())
                .setRelayPort(realtimeProperties.stompRelayPort())
                .setClientLogin(rabbitConnectionDetails.getUsername())
                .setClientPasscode(rabbitConnectionDetails.getPassword())
                .setSystemLogin(rabbitConnectionDetails.getUsername())
                .setSystemPasscode(rabbitConnectionDetails.getPassword())
                .setUserDestinationBroadcast("/topic/unresolved-user-destination")
                .setUserRegistryBroadcast("/topic/simp-user-registry");
    }
}
