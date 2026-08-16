package com.taut0logy.jmeet.meeting.session;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingSessionRepository extends JpaRepository<MeetingSession, String> {

    Optional<MeetingSession> findByMeetingIdAndEndedAtIsNull(String meetingId);

    List<MeetingSession> findByEndedAtIsNull();
}
