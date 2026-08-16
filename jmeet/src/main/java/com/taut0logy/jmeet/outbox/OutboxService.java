package com.taut0logy.jmeet.outbox;

import com.taut0logy.jmeet.job.JobType;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;

    public OutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    public void publish(String aggregateType, String aggregateId, String type, String payloadJson) {
        repository.save(new OutboxEvent(aggregateType, aggregateId, type, payloadJson));
    }

    public void dispatchJob(JobType type, String aggregateId, String payloadJson) {
        publish(Outbox.JOB_AGGREGATE_TYPE, aggregateId, type.key(), payloadJson);
    }
}
