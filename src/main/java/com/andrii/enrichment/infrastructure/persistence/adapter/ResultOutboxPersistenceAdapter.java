package com.andrii.enrichment.infrastructure.persistence.adapter;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.OutboxEvent;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;
import com.andrii.enrichment.infrastructure.persistence.mapper.OutboxEventEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.mapper.ResultEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResultOutboxPersistenceAdapter {

  ResultRepository resultRepository;
  OutboxEventRepository outboxEventRepository;
  ResultEntityMapper resultEntityMapper;
  OutboxEventEntityMapper outboxEventEntityMapper;

  @Transactional
  public PersistedEnrichmentResult persist(EnrichmentResult enrichmentResult, OutboxEvent outboxEvent) {
    log.info("Persisting result and outbox event for messageId={}", enrichmentResult.messageId());

    var resultEntity = resultRepository.save(resultEntityMapper.toEntity(enrichmentResult));
    outboxEventRepository.save(outboxEventEntityMapper.toEntity(outboxEvent));

    return new PersistedEnrichmentResult(resultEntity.getId(), enrichmentResult);
  }
}
