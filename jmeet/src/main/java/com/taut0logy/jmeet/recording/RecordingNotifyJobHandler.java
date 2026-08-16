package com.taut0logy.jmeet.recording;

import com.taut0logy.jmeet.auth.AppUser;
import com.taut0logy.jmeet.auth.AppUserRepository;
import com.taut0logy.jmeet.config.ClientProperties;
import com.taut0logy.jmeet.job.JobHandler;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.mail.EmailMessage;
import com.taut0logy.jmeet.mail.MailService;
import com.taut0logy.jmeet.meeting.Meeting;
import com.taut0logy.jmeet.meeting.MeetingRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RecordingNotifyJobHandler implements JobHandler {

    private final RecordingRepository recordings;
    private final com.taut0logy.jmeet.meeting.MeetingRepository meetings;
    private final AppUserRepository users;
    private final MailService mailService;
    private final ClientProperties clientProperties;
    private final ObjectMapper json;

    public RecordingNotifyJobHandler(RecordingRepository recordings, com.taut0logy.jmeet.meeting.MeetingRepository meetings,
            AppUserRepository users, MailService mailService, ClientProperties clientProperties, ObjectMapper json) {
        this.recordings = recordings;
        this.meetings = meetings;
        this.users = users;
        this.mailService = mailService;
        this.clientProperties = clientProperties;
        this.json = json;
    }

    @Override
    public JobType type() {
        return JobType.RECORDING_NOTIFY;
    }

    @Override
    public void handle(String payload) {
        RecordingNotifyPayload data = json.readValue(payload, RecordingNotifyPayload.class);
        Recording recording = recordings.findById(data.recordingId()).orElse(null);
        Meeting meeting = meetings.findById(data.meetingId()).orElse(null);
        if (recording == null || meeting == null) return;

        AppUser owner = users.findById(meeting.getOwnerId()).orElse(null);
        if (owner == null) return;

        String actionUrl = clientProperties.baseUrl() + "/meetings/" + meeting.getId() + "/recordings";
        mailService.enqueue(recording.getId(), new EmailMessage(owner.getEmail(), "Your recording is ready", "notice",
                Map.of("title", "Your recording is ready",
                        "body", "The recording of \"" + meeting.getTitle() + "\" is ready to download.",
                        "actionUrl", actionUrl, "actionLabel", "View recording")));
    }
}
