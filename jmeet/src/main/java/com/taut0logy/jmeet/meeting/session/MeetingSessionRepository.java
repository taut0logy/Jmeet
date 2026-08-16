package com.taut0logy.jmeet.meeting.session;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingSessionRepository extends JpaRepository<MeetingSession, String> {

    Optional<MeetingSession> findByMeetingIdAndEndedAtIsNull(String meetingId);

    List<MeetingSession> findByEndedAtIsNull();

    /** Serializes concurrent joins to the same session — held for the rest of the caller's
     * transaction, so the room-full check-then-insert in RoomService.join() can't race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MeetingSession s where s.id = :id")
    Optional<MeetingSession> lockForUpdate(@Param("id") String id);
}
