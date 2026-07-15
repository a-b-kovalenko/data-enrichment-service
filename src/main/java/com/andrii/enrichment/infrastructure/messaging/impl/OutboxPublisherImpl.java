package com.andrii.enrichment.infrastructure.messaging.impl;

import com.andrii.enrichment.infrastructure.configuration.OutboxPublisherProperties;
import com.andrii.enrichment.infrastructure.messaging.OutboxPublisher;
import com.andrii.enrichment.infrastructure.messaging.OutputMessagePublisher;
import com.andrii.enrichment.infrastructure.persistence.OutboxEventClaimService;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Clock;
import java.time.Duration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
  prefix = "enrichment.outbox", name = "publisher-enabled", havingValue = "true", matchIfMissing = true
)
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxPublisherImpl implements OutboxPublisher {

  OutboxEventClaimService outboxEventClaimService;
  OutputMessagePublisher outputMessagePublisher;
  OutboxPublisherProperties properties;
  Clock clock;

  @Override
  @Scheduled(fixedDelayString = "${enrichment.outbox.publish-delay}")
  public void publishPendingEvents() {
    var now = clock.instant();
    var events = outboxEventClaimService.claimReadyEvents(now, properties.batchSize(), properties.claimDuration());
    events.forEach(this::publish);
  }

  private void publish(OutboxEventEntity event) {
    try {
      outputMessagePublisher.publish(event.getMessageId(), event.getPayload());
      outboxEventClaimService.markPublished(event.getId(), clock.instant());
    } catch (RuntimeException exception) {
      recordFailure(event, exception);
    }
  }

  private void recordFailure(OutboxEventEntity event, RuntimeException exception) {
    var attempts = event.getAttemptCount() + 1;
    var finalFailure = attempts >= properties.maxAttempts();
    var nextAttemptAt = clock.instant().plus(backoff(attempts));
    outboxEventClaimService.recordFailure(event.getId(), nextAttemptAt, finalFailure);
    if (finalFailure) {
      log.error("Outbox event reached retry limit messageId={}", event.getMessageId(), exception);
    } else {
      log.warn("Output publication failed messageId={}, retryAttempt={}", event.getMessageId(), attempts, exception);
    }
  }

  private Duration backoff(int attempts) {
    var multiplier = 1L << Math.min(attempts - 1, 30);
    var delay = properties.initialBackoff().multipliedBy(multiplier);
    return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
  }
}
