package com.taut0logy.jmeet.job;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

public final class Amqp {

    public static final String JOBS_EXCHANGE = "meet.jobs";
    public static final String DLX_EXCHANGE = "meet.dlx";
    public static final String DEAD_QUEUE = "jobs.dead";
    public static final String DELAY_HEADER = "x-delay";

    private Amqp() {
    }

    public static Message jsonMessage(String messageId, String payload) {
        return MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(messageId)
                .build();
    }
}
