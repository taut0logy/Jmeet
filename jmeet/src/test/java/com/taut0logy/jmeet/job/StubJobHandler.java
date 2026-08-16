package com.taut0logy.jmeet.job;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class StubJobHandler implements JobHandler {

    private final Map<String, AtomicInteger> failuresRemaining = new ConcurrentHashMap<>();
    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    public JobType type() {
        return JobType.MEETING_REMINDER;
    }

    @Override
    public void handle(String payload) throws Exception {
        AtomicInteger remaining = failuresRemaining.get(payload);
        if (remaining != null && remaining.getAndDecrement() > 0) {
            throw new RuntimeException("stub failure for " + payload);
        }
        handled.add(payload);
    }

    public void failNext(String payload, int times) {
        failuresRemaining.put(payload, new AtomicInteger(times));
    }

    public List<String> handledPayloads() {
        return handled;
    }

    public int handledCount(String payload) {
        return (int) handled.stream().filter(payload::equals).count();
    }
}
