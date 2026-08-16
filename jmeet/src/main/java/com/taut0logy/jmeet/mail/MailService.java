package com.taut0logy.jmeet.mail;

import tools.jackson.databind.ObjectMapper;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.outbox.OutboxService;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public MailService(OutboxService outboxService, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String aggregateId, EmailMessage email) {
        try {
            String payload = objectMapper.writeValueAsString(email);
            outboxService.dispatchJob(JobType.EMAIL_SEND, aggregateId, payload);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize email message", e);
        }
    }
}
