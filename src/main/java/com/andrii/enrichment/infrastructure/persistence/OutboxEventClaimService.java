package com.andrii.enrichment.infrastructure.persistence;

import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface OutboxEventClaimService {

  List<OutboxEventEntity> claimReadyEvents(Instant now, int batchSize, Duration claimDuration);

  void markPublished(Long eventId, Instant publishedAt);

  void recordFailure(Long eventId, Instant nextAttemptAt, boolean finalFailure);
}
