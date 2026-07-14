package com.andrii.enrichment.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.OutboxEvent;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.ResultEntity;
import com.andrii.enrichment.infrastructure.persistence.mapper.OutboxEventEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.mapper.ResultEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultOutboxPersistenceAdapterTest {

  private static final Instant CREATED_AT = Instant.parse("2026-07-01T10:00:01Z");
  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Mock
  private ResultRepository resultRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private ResultEntityMapper resultEntityMapper;

  @Mock
  private OutboxEventEntityMapper outboxEventEntityMapper;

  @InjectMocks
  private ResultOutboxPersistenceAdapter resultOutboxPersistenceAdapter;

  @Test
  void persistsResultAndOutboxEvent() {
    var enrichmentResult = newEnrichmentResult();
    var outboxEvent = new OutboxEvent(MESSAGE_ID, "payload", CREATED_AT, CREATED_AT);
    var resultEntity = ResultEntity.builder().id(123L).build();
    var outboxEventEntity = OutboxEventEntity.builder().build();

    given(resultEntityMapper.toEntity(enrichmentResult)).willReturn(resultEntity);
    given(resultRepository.save(resultEntity)).willReturn(resultEntity);
    given(outboxEventEntityMapper.toEntity(outboxEvent)).willReturn(outboxEventEntity);

    assertThat(resultOutboxPersistenceAdapter.persist(enrichmentResult, outboxEvent))
      .extracting("logId", "enrichmentResult.messageId")
      .containsExactly(123L, MESSAGE_ID);
    then(outboxEventRepository).should().save(outboxEventEntity);
  }

  private EnrichmentResult newEnrichmentResult() {
    return new EnrichmentResult(MESSAGE_ID, 12345678L, "request", true, CREATED_AT, CREATED_AT);
  }
}
