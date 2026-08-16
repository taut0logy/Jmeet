package com.taut0logy.jmeet.recording;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingRepository extends JpaRepository<Recording, String> {

    Optional<Recording> findByEgressId(String egressId);

    Optional<Recording> findBySessionIdAndStatus(String sessionId, RecordingStatus status);

    List<Recording> findByMeetingIdAndStatusIn(String meetingId, List<RecordingStatus> statuses);

    List<Recording> findByStatusIn(List<RecordingStatus> statuses);

    List<Recording> findBySessionIdAndStatusIn(String sessionId, List<RecordingStatus> statuses);

    List<Recording> findByMeetingIdOrderByStartedAtDesc(String meetingId);

    List<Recording> findByStatusInAndStartedAtBefore(List<RecordingStatus> statuses, Instant threshold);
}
