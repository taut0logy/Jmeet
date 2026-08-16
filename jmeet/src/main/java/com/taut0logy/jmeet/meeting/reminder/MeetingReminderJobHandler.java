package com.taut0logy.jmeet.meeting.reminder;

import com.taut0logy.jmeet.config.ClientProperties;
import com.taut0logy.jmeet.job.JobHandler;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.mail.EmailMessage;
import com.taut0logy.jmeet.mail.MailService;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MeetingReminderJobHandler implements JobHandler {

    private final MailService mailService;
    private final ClientProperties clientProperties;
    private final ObjectMapper json;

    public MeetingReminderJobHandler(MailService mailService, ClientProperties clientProperties, ObjectMapper json) {
        this.mailService = mailService;
        this.clientProperties = clientProperties;
        this.json = json;
    }

    @Override
    public JobType type() {
        return JobType.MEETING_REMINDER;
    }

    @Override
    public void handle(String payload) {
        MeetingReminderPayload data = json.readValue(payload, MeetingReminderPayload.class);
        String actionUrl = clientProperties.baseUrl() + "/meetings/" + data.meetingId();
        mailService.enqueue(data.meetingId() + ":" + data.recipientEmail(), new EmailMessage(data.recipientEmail(),
                "Reminder: " + data.meetingTitle(), "notice",
                Map.of("title", "Upcoming meeting reminder",
                        "body", "\"" + data.meetingTitle() + "\" starts soon.",
                        "actionUrl", actionUrl, "actionLabel", "View meeting")));
    }
}
