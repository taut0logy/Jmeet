package com.taut0logy.jmeet.meeting.reminder;

import java.time.Instant;

record MeetingReminderPayload(String meetingId, String recipientEmail, String meetingTitle, Instant occurrenceStartsAt) {
}
