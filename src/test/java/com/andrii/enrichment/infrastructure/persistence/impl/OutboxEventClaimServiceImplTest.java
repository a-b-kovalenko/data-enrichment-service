package com.andrii.enrichment.infrastructure.persistence.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventStatus;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-07-01T10:00:00Z");

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Test
  void claimsReadyEventsAndSavesEachClaim() {
    var event = event();
    var service = new OutboxEventClaimServiceImpl(outboxEventRepository);
    when(outboxEventRepository.lockReadyForPublishing(NOW, 20)).thenReturn(List.of(event));

    service.claimReadyEvents(NOW, 20, Duration.ofSeconds(30));

    verify(outboxEventRepository).save(event);
  }

  @Test
  void marksEventPublishedAndSavesIt() {
    var event = event();
    var service = new OutboxEventClaimServiceImpl(outboxEventRepository);
    when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

    service.markPublished(1L, NOW);

    assertThat(event)
      .extracting("status", "publishedAt")
      .containsExactly(OutboxEventStatus.PUBLISHED, NOW);
    verify(outboxEventRepository).save(event);
  }

  private OutboxEventEntity event() {
    return OutboxEventEntity.builder().id(1L).attemptCount(0).nextAttemptAt(NOW).createdAt(NOW).build();
  }
}
