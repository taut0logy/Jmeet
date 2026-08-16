package com.taut0logy.jmeet.meeting;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, String> {

    Optional<Meeting> findByCode(String code);

    boolean existsByCode(String code);

    List<Meeting> findByOwnerId(String ownerId);
}
