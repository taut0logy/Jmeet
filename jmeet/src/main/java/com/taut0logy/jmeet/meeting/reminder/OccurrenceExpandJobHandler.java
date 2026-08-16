package com.taut0logy.jmeet.meeting.reminder;

import com.taut0logy.jmeet.auth.AppUser;
import com.taut0logy.jmeet.auth.AppUserRepository;
import com.taut0logy.jmeet.job.JobHandler;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.meeting.Meeting;
import com.taut0logy.jmeet.meeting.MeetingRepository;
import com.taut0logy.jmeet.meeting.member.MeetingMember;
import com.taut0logy.jmeet.meeting.member.MeetingMemberRepository;
import com.taut0logy.jmeet.outbox.OutboxService;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Fans one due occurrence out into one meeting.reminder job per recipient — owner plus every
 * member, whether they have an account yet or are still a pending email-only invite. */
@Component
public class OccurrenceExpandJobHandler implements JobHandler {

    private final MeetingRepository meetings;
    private final MeetingMemberRepository members;
    private final AppUserRepository users;
    private final OutboxService outbox;
    private final ObjectMapper json;

    public OccurrenceExpandJobHandler(MeetingRepository meetings, MeetingMemberRepository members, AppUserRepository users,
            OutboxService outbox, ObjectMapper json) {
        this.meetings = meetings;
        this.members = members;
        this.users = users;
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public JobType type() {
        return JobType.OCCURRENCE_EXPAND;
    }

    @Override
    public void handle(String payload) {
        OccurrenceExpandPayload data = json.readValue(payload, OccurrenceExpandPayload.class);
        Meeting meeting = meetings.findById(data.meetingId()).orElse(null);
        if (meeting == null) return;

        Set<String> recipients = new LinkedHashSet<>();
        users.findById(meeting.getOwnerId()).map(AppUser::getEmail).ifPresent(recipients::add);
        for (MeetingMember member : members.findByMeetingId(meeting.getId())) {
            if (member.getUserId() != null) {
                users.findById(member.getUserId()).map(AppUser::getEmail).ifPresent(recipients::add);
            } else if (member.getEmail() != null) {
                recipients.add(member.getEmail());
            }
        }

        for (String email : recipients) {
            MeetingReminderPayload reminder = new MeetingReminderPayload(meeting.getId(), email, meeting.getTitle(),
                    data.occurrenceStartsAt());
            outbox.dispatchJob(JobType.MEETING_REMINDER, meeting.getId() + ":" + email, json.writeValueAsString(reminder));
        }
    }
}
