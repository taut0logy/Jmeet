package com.taut0logy.jmeet.job;

public interface JobHandler {

    JobType type();

    void handle(String payload) throws Exception;
}
