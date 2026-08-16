package com.taut0logy.jmeet.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(DataRedisConnectionDetails connectionDetails) {
        DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort())
                .withDatabase(standalone.getDatabase());
        if (connectionDetails.getPassword() != null) {
            uri.withPassword(connectionDetails.getPassword().toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager(RedisClient redisClient) {
        return Bucket4jLettuce.casBasedBuilder(redisClient).build();
    }
}
