package com.andrii.enrichment.infrastructure.messaging.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.infrastructure.configuration.OutboxPublisherProperties;
import com.andrii.enrichment.infrastructure.messaging.OutputMessagePublisher;
import com.andrii.enrichment.infrastructure.persistence.OutboxEventClaimService;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherImplTest {

  private static final Instant NOW = Instant.parse("2026-07-01T10:00:00Z");
  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Mock
  private OutboxEventClaimService outboxEventClaimService;

  @Mock
  private OutputMessagePublisher outputMessagePublisher;

  @Test
  void marksEventPublishedAfterBrokerConfirm() {
    var event = event(0);
    var service = service(3);
    when(outboxEventClaimService.claimReadyEvents(any(), eq(20), any())).thenReturn(List.of(event));

    service.publishPendingEvents();

    verify(outputMessagePublisher).publish(MESSAGE_ID, "payload");
    verify(outboxEventClaimService).markPublished(event.getId(), NOW);
  }

  @Test
  void schedulesRetryWithExponentialBackoffAfterPublishFailure() {
    var event = event(1);
    var service = service(3);
    when(outboxEventClaimService.claimReadyEvents(any(), eq(20), any())).thenReturn(List.of(event));
    doThrow(new IllegalStateException("nack")).when(outputMessagePublisher).publish(MESSAGE_ID, "payload");

    service.publishPendingEvents();

    verify(outboxEventClaimService).recordFailure(event.getId(), NOW.plusSeconds(10), false);
  }

  @Test
  void marksEventFailedAfterRetryLimit() {
    var event = event(2);
    var service = service(3);
    when(outboxEventClaimService.claimReadyEvents(any(), eq(20), any())).thenReturn(List.of(event));
    doThrow(new IllegalStateException("nack")).when(outputMessagePublisher).publish(MESSAGE_ID, "payload");

    service.publishPendingEvents();

    verify(outboxEventClaimService).recordFailure(event.getId(), NOW.plusSeconds(20), true);
  }

  @Test
  void doesNotPublishWhenBatchIsEmpty() {
    var service = service(3);
    when(outboxEventClaimService.claimReadyEvents(any(), eq(20), any())).thenReturn(List.of());

    service.publishPendingEvents();

    verifyNoInteractions(outputMessagePublisher);
  }

  private OutboxPublisherImpl service(int maxAttempts) {
    var properties = new OutboxPublisherProperties(
      "output", "output", 20, Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30),
      Duration.ofSeconds(5), Duration.ofMinutes(5), maxAttempts
    );
    return new OutboxPublisherImpl(outboxEventClaimService, outputMessagePublisher, properties,
      Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private OutboxEventEntity event(int attemptCount) {
    return OutboxEventEntity.builder()
      .id(1L)
      .messageId(MESSAGE_ID)
      .payload("payload")
      .attemptCount(attemptCount)
      .nextAttemptAt(NOW)
      .createdAt(NOW)
      .build();
  }
}
