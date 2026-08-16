package com.taut0logy.jmeet.meeting.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingMemberRepository extends JpaRepository<MeetingMember, String> {

    List<MeetingMember> findByMeetingId(String meetingId);

    Optional<MeetingMember> findByMeetingIdAndUserId(String meetingId, String userId);

    Optional<MeetingMember> findByMeetingIdAndEmailAndUserIdIsNull(String meetingId, String email);

    List<MeetingMember> findByUserId(String userId);
}
