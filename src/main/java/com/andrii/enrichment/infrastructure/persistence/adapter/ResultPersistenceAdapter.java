package com.andrii.enrichment.infrastructure.persistence.adapter;

import com.andrii.enrichment.application.exception.DuplicateMessageException;
import com.andrii.enrichment.application.port.ResultPersistencePort;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;
import com.andrii.enrichment.infrastructure.persistence.mapper.ResultEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResultPersistenceAdapter implements ResultPersistencePort {

  ResultRepository resultRepository;
  ResultEntityMapper resultEntityMapper;

  @Override
  public PersistedEnrichmentResult save(EnrichmentResult enrichmentResult) {
    log.info("Persisting result for messageId={}", enrichmentResult.messageId());

    try {
      var resultEntity = resultRepository.saveAndFlush(resultEntityMapper.toEntity(enrichmentResult));

      return toPersistedResult(resultEntity.getId(), enrichmentResult);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateMessageException(enrichmentResult.messageId(), exception);
    }
  }

  @Override
  public Optional<PersistedEnrichmentResult> findByMessageId(UUID messageId) {
    return resultRepository.findByMessageId(messageId)
      .map(resultEntity -> toPersistedResult(resultEntity.getId(), resultEntityMapper.toDomain(resultEntity)));
  }

  private PersistedEnrichmentResult toPersistedResult(long logId, EnrichmentResult enrichmentResult) {
    return new PersistedEnrichmentResult(logId, enrichmentResult);
  }
}
