package com.andrii.enrichment.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

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

    given(resultEntityMapper.toEntity(enrichmentResult)).willReturn(resultEntity);
    given(resultRepository.saveAndFlush(resultEntity)).willThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> resultPersistenceAdapter.save(enrichmentResult))
      .isInstanceOf(DuplicateMessageException.class)
      .hasMessageContaining(MESSAGE_ID.toString());
  }

  @Test
  void savesAndReturnsPersistedResult() {
    var enrichmentResult = newEnrichmentResult();
    var mappedEntity = ResultEntity.builder().build();
    var persistedEntity = ResultEntity.builder().id(123L).build();

    given(resultEntityMapper.toEntity(enrichmentResult)).willReturn(mappedEntity);
    given(resultRepository.saveAndFlush(mappedEntity)).willReturn(persistedEntity);

    assertThat(resultPersistenceAdapter.save(enrichmentResult))
      .extracting("logId", "enrichmentResult.messageId")
      .containsExactly(123L, MESSAGE_ID);
  }

  @Test
  void returnsPersistedResultWhenMessageIdExists() {
    var resultEntity = ResultEntity.builder().id(123L).build();
    var enrichmentResult = newEnrichmentResult();

    given(resultRepository.findByMessageId(MESSAGE_ID)).willReturn(Optional.of(resultEntity));
    given(resultEntityMapper.toDomain(resultEntity)).willReturn(enrichmentResult);

    assertThat(resultPersistenceAdapter.findByMessageId(MESSAGE_ID))
      .isPresent()
      .get()
      .extracting("logId", "enrichmentResult.action")
      .containsExactly(123L, "request");
  }

  @Test
  void returnsEmptyWhenMessageIdDoesNotExist() {
    given(resultRepository.findByMessageId(MESSAGE_ID)).willReturn(Optional.empty());

    assertThat(resultPersistenceAdapter.findByMessageId(MESSAGE_ID)).isEmpty();
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
