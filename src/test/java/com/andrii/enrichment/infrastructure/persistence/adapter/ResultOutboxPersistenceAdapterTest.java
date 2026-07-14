package com.andrii.enrichment.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.OutboxEvent;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.ResultEntity;
import com.andrii.enrichment.infrastructure.persistence.mapper.OutboxEventEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.mapper.ResultEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import com.andrii.enrichment.infrastructure.messaging.dto.OutgoingMessage;
import com.andrii.enrichment.infrastructure.messaging.mapper.OutgoingMessageMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

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

  @Mock
  private OutgoingMessageMapper outgoingMessageMapper;

  @Mock
  private ObjectMapper objectMapper;

  @InjectMocks
  private ResultOutboxPersistenceAdapter resultOutboxPersistenceAdapter;

  @Test
  void persistsResultAndOutboxEvent() {
    var enrichmentResult = newEnrichmentResult();
    var outboxEvent = new OutboxEvent(MESSAGE_ID, "payload", CREATED_AT, CREATED_AT);
    var resultEntity = ResultEntity.builder().id(123L).build();
    var outboxEventEntity = OutboxEventEntity.builder().build();

    when(resultEntityMapper.toEntity(enrichmentResult)).thenReturn(resultEntity);
    when(resultRepository.save(resultEntity)).thenReturn(resultEntity);
    when(outboxEventEntityMapper.toEntity(outboxEvent)).thenReturn(outboxEventEntity);

    var result = resultOutboxPersistenceAdapter.persist(enrichmentResult, outboxEvent);

    assertThat(result)
      .extracting("logId", "enrichmentResult.messageId")
      .containsExactly(123L, MESSAGE_ID);
    verify(outboxEventRepository).save(outboxEventEntity);
  }

  @Test
  void persistsGeneratedOutboxEventWithFinalLogId() throws Exception {
    var enrichmentResult = newEnrichmentResult();
    var resultEntity = ResultEntity.builder().id(123L).build();
    var outgoingMessage = new OutgoingMessage(123L, MESSAGE_ID, true);
    var outboxEventEntity = OutboxEventEntity.builder().build();

    when(resultEntityMapper.toEntity(enrichmentResult)).thenReturn(resultEntity);
    when(resultRepository.save(resultEntity)).thenReturn(resultEntity);
    when(outgoingMessageMapper.toOutgoingMessage(123L, enrichmentResult)).thenReturn(outgoingMessage);
    when(objectMapper.writeValueAsString(outgoingMessage)).thenReturn("payload");
    when(outboxEventEntityMapper.toEntity(any(OutboxEvent.class))).thenReturn(outboxEventEntity);

    var result = resultOutboxPersistenceAdapter.persist(enrichmentResult);

    assertThat(result)
      .extracting("logId", "enrichmentResult.messageId")
      .containsExactly(123L, MESSAGE_ID);
    verify(outboxEventRepository).save(outboxEventEntity);
  }


  private EnrichmentResult newEnrichmentResult() {
    return new EnrichmentResult(MESSAGE_ID, 12345678L, "request", true, CREATED_AT, CREATED_AT);
  }
}
