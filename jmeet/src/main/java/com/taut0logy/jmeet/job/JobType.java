package com.taut0logy.jmeet.job;

import java.util.Map;
import java.util.stream.Stream;

public enum JobType {
    EMAIL_SEND("email.send"),
    RECORDING_NOTIFY("recording.notify"),
    OCCURRENCE_EXPAND("occurrence.expand"),
    MEETING_REMINDER("meeting.reminder"),
    SESSION_CLOSE("session.close");

    private static final Map<String, JobType> BY_KEY =
            Stream.of(values()).collect(java.util.stream.Collectors.toMap(JobType::key, t -> t));

    private final String key;

    JobType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String queueName() {
        return "jobs." + key;
    }

    public static JobType fromKey(String key) {
        JobType type = BY_KEY.get(key);
        if (type == null) {
            throw new IllegalArgumentException("unknown job type: " + key);
        }
        return type;
    }

    public static boolean isJobType(String key) {
        return BY_KEY.containsKey(key);
    }
}
