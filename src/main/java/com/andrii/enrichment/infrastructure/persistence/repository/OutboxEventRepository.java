package com.andrii.enrichment.infrastructure.persistence.repository;

import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

  @Query("""
    SELECT outboxEvent FROM OutboxEventEntity outboxEvent
    WHERE outboxEvent.status = :status
      AND outboxEvent.nextAttemptAt <= :nextAttemptAt
    ORDER BY outboxEvent.nextAttemptAt, outboxEvent.id
    """)
  List<OutboxEventEntity> findReadyForPublishing(
    @Param("status") OutboxEventStatus status,
    @Param("nextAttemptAt") Instant nextAttemptAt
  );

  @Query(value = """
    SELECT * FROM outbox_event
    WHERE status = 'PENDING'
      AND next_attempt_at <= :now
    ORDER BY next_attempt_at, id
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
  List<OutboxEventEntity> lockReadyForPublishing(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
