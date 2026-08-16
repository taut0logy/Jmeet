package com.taut0logy.jmeet.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            select * from outbox_event
             where published_at is null and failed_at is null and next_attempt_at <= now()
             order by created_at
             for update skip locked
             limit :limit
            """, nativeQuery = true)
    List<OutboxEvent> claimPending(@Param("limit") int limit);
}
