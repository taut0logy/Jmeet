package com.taut0logy.jmeet.meeting.session;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, String> {

    List<Participation> findBySessionIdAndLeftAtIsNull(String sessionId);

    Optional<Participation> findBySessionIdAndPeerId(String sessionId, String peerId);

    List<Participation> findByUserIdAndLeftAtIsNull(String userId);
}
