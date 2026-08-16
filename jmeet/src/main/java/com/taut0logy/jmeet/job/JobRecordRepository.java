package com.taut0logy.jmeet.job;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRecordRepository extends JpaRepository<JobRecord, UUID> {
}
