package com.taut0logy.jmeet.outbox;

public final class Outbox {

    public static final String EVENTS_EXCHANGE = "meet.events";
    public static final String JOB_AGGREGATE_TYPE = "job";

    private Outbox() {
    }

    public static boolean isJobDispatch(OutboxEvent event) {
        return JOB_AGGREGATE_TYPE.equals(event.getAggregateType());
    }
}
