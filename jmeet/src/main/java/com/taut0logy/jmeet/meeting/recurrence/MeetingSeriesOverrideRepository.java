package com.taut0logy.jmeet.meeting.recurrence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingSeriesOverrideRepository extends JpaRepository<MeetingSeriesOverride, String> {

    List<MeetingSeriesOverride> findByMeetingIdOrderByFromStartsAt(String meetingId);
}
