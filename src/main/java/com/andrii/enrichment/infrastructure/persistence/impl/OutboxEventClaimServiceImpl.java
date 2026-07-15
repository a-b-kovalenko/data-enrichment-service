package com.andrii.enrichment.infrastructure.persistence.impl;

import com.andrii.enrichment.infrastructure.persistence.OutboxEventClaimService;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventStatus;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventClaimServiceImpl implements OutboxEventClaimService {

  OutboxEventRepository outboxEventRepository;

  /**
   * Claims ready outbox events so that other publisher instances cannot process them concurrently.
   */
  @Transactional
  @Override
  public List<OutboxEventEntity> claimReadyEvents(Instant now, int batchSize, Duration claimDuration) {
    var events = outboxEventRepository.lockReadyForPublishing(now, batchSize);
    var claimedUntil = now.plus(claimDuration);
    events.forEach(event -> claim(event, claimedUntil));
    return events;
  }

  /**
   * Marks an event as published after RabbitMQ confirms the broker delivery.
   */
  @Transactional
  @Override
  public void markPublished(Long eventId, Instant publishedAt) {
    var event = getEvent(eventId);
    event.setStatus(OutboxEventStatus.PUBLISHED);
    event.setPublishedAt(publishedAt);
    outboxEventRepository.save(event);
  }

  /**
   * Records a failed publication and moves the event to its final state when required.
   */
  @Transactional
  @Override
  public void recordFailure(Long eventId, Instant nextAttemptAt, boolean finalFailure) {
    var event = getEvent(eventId);
    event.setAttemptCount(event.getAttemptCount() + 1);
    event.setNextAttemptAt(nextAttemptAt);
    if (finalFailure) {
      event.setStatus(OutboxEventStatus.FAILED);
    }
    outboxEventRepository.save(event);
  }

  private OutboxEventEntity getEvent(Long eventId) {
    return outboxEventRepository.findById(eventId)
      .orElseThrow(() -> new IllegalStateException("Outbox event does not exist: " + eventId));
  }

  private void claim(OutboxEventEntity event, Instant claimedUntil) {
    event.setNextAttemptAt(claimedUntil);
    outboxEventRepository.save(event);
  }
}
