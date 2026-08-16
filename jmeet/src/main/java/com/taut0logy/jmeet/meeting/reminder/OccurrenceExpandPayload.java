package com.taut0logy.jmeet.meeting.reminder;

import java.time.Instant;

record OccurrenceExpandPayload(String meetingId, Instant occurrenceStartsAt) {
}
