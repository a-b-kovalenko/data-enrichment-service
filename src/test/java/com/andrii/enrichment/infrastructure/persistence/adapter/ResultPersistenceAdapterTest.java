package com.andrii.enrichment.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.application.exception.DuplicateMessageException;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.infrastructure.persistence.entity.ResultEntity;
import com.andrii.enrichment.infrastructure.persistence.mapper.ResultEntityMapper;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ResultPersistenceAdapterTest {

  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Mock
  private ResultRepository resultRepository;

  @Mock
  private ResultEntityMapper resultEntityMapper;

  @InjectMocks
  private ResultPersistenceAdapter resultPersistenceAdapter;

  @Test
  void translatesDatabaseDuplicateIntoApplicationException() {
    var enrichmentResult = newEnrichmentResult();
    var resultEntity = ResultEntity.builder().build();

    when(resultEntityMapper.toEntity(enrichmentResult)).thenReturn(resultEntity);
    when(resultRepository.saveAndFlush(resultEntity)).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> resultPersistenceAdapter.save(enrichmentResult))
      .isInstanceOf(DuplicateMessageException.class)
      .hasMessageContaining(MESSAGE_ID.toString());
  }

  @Test
  void savesAndReturnsPersistedResult() {
    var enrichmentResult = newEnrichmentResult();
    var mappedEntity = ResultEntity.builder().build();
    var persistedEntity = ResultEntity.builder().id(123L).build();

    when(resultEntityMapper.toEntity(enrichmentResult)).thenReturn(mappedEntity);
    when(resultRepository.saveAndFlush(mappedEntity)).thenReturn(persistedEntity);

    var result = resultPersistenceAdapter.save(enrichmentResult);

    assertThat(result)
      .extracting("logId", "enrichmentResult.messageId")
      .containsExactly(123L, MESSAGE_ID);
  }

  @Test
  void returnsPersistedResultWhenMessageIdExists() {
    var resultEntity = ResultEntity.builder().id(123L).build();
    var enrichmentResult = newEnrichmentResult();

    when(resultRepository.findByMessageId(MESSAGE_ID)).thenReturn(Optional.of(resultEntity));
    when(resultEntityMapper.toDomain(resultEntity)).thenReturn(enrichmentResult);

    var result = resultPersistenceAdapter.findByMessageId(MESSAGE_ID);

    assertThat(result)
      .isPresent()
      .get()
      .extracting("logId", "enrichmentResult.action")
      .containsExactly(123L, "request");
  }

  @Test
  void returnsEmptyWhenMessageIdDoesNotExist() {
    when(resultRepository.findByMessageId(MESSAGE_ID)).thenReturn(Optional.empty());

    var result = resultPersistenceAdapter.findByMessageId(MESSAGE_ID);

    assertThat(result).isEmpty();
  }

  private EnrichmentResult newEnrichmentResult() {
    return new EnrichmentResult(
      MESSAGE_ID,
      12345678L,
      "request",
      true,
      Instant.parse("2026-07-01T10:00:00Z"),
      Instant.parse("2026-07-01T10:00:01Z")
    );
  }
}
