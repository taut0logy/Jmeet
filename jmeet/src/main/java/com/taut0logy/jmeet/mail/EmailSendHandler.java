package com.taut0logy.jmeet.mail;

import tools.jackson.databind.ObjectMapper;
import com.taut0logy.jmeet.job.JobHandler;
import com.taut0logy.jmeet.job.JobType;
import org.springframework.stereotype.Component;

@Component
public class EmailSendHandler implements JobHandler {

    private final Mailer mailer;
    private final ObjectMapper objectMapper;

    public EmailSendHandler(Mailer mailer, ObjectMapper objectMapper) {
        this.mailer = mailer;
        this.objectMapper = objectMapper;
    }

    @Override
    public JobType type() {
        return JobType.EMAIL_SEND;
    }

    @Override
    public void handle(String payload) throws Exception {
        EmailMessage email = objectMapper.readValue(payload, EmailMessage.class);
        mailer.send(email);
    }
}
