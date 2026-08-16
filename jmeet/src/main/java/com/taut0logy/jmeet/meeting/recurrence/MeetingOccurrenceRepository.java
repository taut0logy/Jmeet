package com.taut0logy.jmeet.meeting.recurrence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingOccurrenceRepository extends JpaRepository<MeetingOccurrence, String> {

    List<MeetingOccurrence> findByMeetingId(String meetingId);

    Optional<MeetingOccurrence> findByMeetingIdAndOriginalStartsAt(String meetingId, Instant originalStartsAt);
}
