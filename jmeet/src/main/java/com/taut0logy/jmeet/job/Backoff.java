package com.taut0logy.jmeet.job;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class Backoff {

    private Backoff() {
    }

    public static Duration compute(int attempts, Duration base, Duration cap) {
        double raw = base.toMillis() * Math.pow(2, attempts);
        long capped = Math.min((long) raw, cap.toMillis());
        double jitter = 0.5 + ThreadLocalRandom.current().nextDouble(0.5);
        return Duration.ofMillis((long) (capped * jitter));
    }
}
