package com.taut0logy.jmeet.room.livekit;

import com.taut0logy.jmeet.config.LiveKitProperties;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LiveKitConfig {

    @Bean
    RoomServiceClient roomServiceClient(LiveKitProperties properties) {
        return RoomServiceClient.createClient(properties.host(), properties.apiKey(), properties.apiSecret());
    }

    @Bean
    WebhookReceiver webhookReceiver(LiveKitProperties properties) {
        return new WebhookReceiver(properties.apiKey(), properties.apiSecret());
    }
}
