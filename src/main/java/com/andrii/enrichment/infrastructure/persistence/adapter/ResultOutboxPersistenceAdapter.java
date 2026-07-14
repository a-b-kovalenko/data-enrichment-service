package com.andrii.enrichment.infrastructure.persistence.adapter;

import com.andrii.enrichment.application.exception.DuplicateMessageException;
import com.andrii.enrichment.application.port.ResultOutboxPersistencePort;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.OutboxEvent;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;
import com.andrii.enrichment.infrastructure.persistence.mapper.OutboxEventEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.mapper.ResultEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import com.andrii.enrichment.infrastructure.messaging.dto.OutgoingMessage;
import com.andrii.enrichment.infrastructure.messaging.mapper.OutgoingMessageMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResultOutboxPersistenceAdapter implements ResultOutboxPersistencePort {

  ResultRepository resultRepository;
  OutboxEventRepository outboxEventRepository;
  ResultEntityMapper resultEntityMapper;
  OutboxEventEntityMapper outboxEventEntityMapper;
  OutgoingMessageMapper outgoingMessageMapper;
  ObjectMapper objectMapper;

  @Transactional
  @Override
  public PersistedEnrichmentResult persist(EnrichmentResult enrichmentResult) {
    log.info("Persisting result and outbox event for messageId={}", enrichmentResult.messageId());

    try {
      var resultEntity = resultRepository.save(resultEntityMapper.toEntity(enrichmentResult));
      var persistedResult = new PersistedEnrichmentResult(resultEntity.getId(), enrichmentResult);
      outboxEventRepository.save(outboxEventEntityMapper.toEntity(outboxEvent(persistedResult)));

      return persistedResult;
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateMessageException(enrichmentResult.messageId(), exception);
    }
  }

  @Transactional
  public PersistedEnrichmentResult persist(EnrichmentResult enrichmentResult, OutboxEvent outboxEvent) {
    log.info("Persisting result and supplied outbox event for messageId={}", enrichmentResult.messageId());

    try {
      var resultEntity = resultRepository.save(resultEntityMapper.toEntity(enrichmentResult));
      outboxEventRepository.save(outboxEventEntityMapper.toEntity(outboxEvent));

      return new PersistedEnrichmentResult(resultEntity.getId(), enrichmentResult);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateMessageException(enrichmentResult.messageId(), exception);
    }
  }

  private OutboxEvent outboxEvent(PersistedEnrichmentResult persistedResult) {
    var outgoingMessage = outgoingMessageMapper.toOutgoingMessage(
      persistedResult.logId(), persistedResult.enrichmentResult());
    return new OutboxEvent(
      persistedResult.enrichmentResult().messageId(), payload(outgoingMessage),
      persistedResult.enrichmentResult().createdAt(), persistedResult.enrichmentResult().createdAt());
  }

  private String payload(OutgoingMessage outgoingMessage) {
    try {
      return objectMapper.writeValueAsString(outgoingMessage);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Cannot serialize outbox event", exception);
    }
  }
}
