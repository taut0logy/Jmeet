package com.taut0logy.jmeet.common;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final ProxyManager<byte[]> proxyManager;

    public RateLimiter(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /** Throws RATE_LIMIT_EXCEEDED if the bucket for this key has no tokens left. */
    public void check(String key, int limit, Duration period) {
        byte[] bucketKey = key.getBytes(StandardCharsets.UTF_8);
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillIntervally(limit, period).build())
                .build();
        boolean allowed = proxyManager.builder().build(bucketKey, () -> config).tryConsume(1);
        if (!allowed) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED, "Too many requests. Please try again later.");
        }
    }
}
