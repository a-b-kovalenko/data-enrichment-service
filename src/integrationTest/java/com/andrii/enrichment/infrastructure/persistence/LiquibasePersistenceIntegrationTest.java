package com.andrii.enrichment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.andrii.enrichment.application.exception.DuplicateMessageException;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.OutboxEvent;
import com.andrii.enrichment.infrastructure.persistence.adapter.ResultOutboxPersistenceAdapter;
import com.andrii.enrichment.infrastructure.persistence.adapter.ResultPersistenceAdapter;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventStatus;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class LiquibasePersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-07-01T10:00:01Z");
  private static final Instant EVENT_TIMESTAMP = Instant.parse("2026-07-01T10:00:00Z");
  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ResultPersistenceAdapter resultPersistenceAdapter;

  @Autowired
  private ResultOutboxPersistenceAdapter resultOutboxPersistenceAdapter;

  @Autowired
  private ResultRepository resultRepository;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void clearDatabase() {
    outboxEventRepository.deleteAll();
    resultRepository.deleteAll();
  }

  @Test
  void createsSchemaWithLiquibaseAndValidatesItWithHibernate() {
    assertThat(tableExists("result")).isTrue();
    assertThat(tableExists("outbox_event")).isTrue();
    assertThat(indexExists("idx_outbox_event_pending")).isTrue();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class)).isEqualTo(2);
  }

  @Test
  void persistsAndReadsResultByMessageId() {
    var persistedResult = resultPersistenceAdapter.save(newEnrichmentResult(MESSAGE_ID));

    assertThat(resultPersistenceAdapter.findByMessageId(MESSAGE_ID))
      .isPresent()
      .get()
      .extracting("logId", "enrichmentResult.messageId", "enrichmentResult.createdAt")
      .containsExactly(persistedResult.logId(), MESSAGE_ID, CREATED_AT);
  }

  @Test
  void rejectsDuplicateMessageId() {
    resultPersistenceAdapter.save(newEnrichmentResult(MESSAGE_ID));

    assertThatThrownBy(() -> resultPersistenceAdapter.save(newEnrichmentResult(MESSAGE_ID)))
      .isInstanceOf(DuplicateMessageException.class);
  }

  @Test
  void commitsResultAndOutboxEventInOneTransaction() {
    resultOutboxPersistenceAdapter.persist(newEnrichmentResult(MESSAGE_ID), newOutboxEvent("payload"));

    assertThat(resultRepository.count()).isEqualTo(1);
    assertThat(outboxEventRepository.count()).isEqualTo(1);
  }

  @Test
  void duplicateDoesNotCreateSecondResultOrOutboxEvent() {
    resultOutboxPersistenceAdapter.persist(newEnrichmentResult(MESSAGE_ID));

    assertThatThrownBy(() -> resultOutboxPersistenceAdapter.persist(newEnrichmentResult(MESSAGE_ID)))
      .isInstanceOf(DuplicateMessageException.class);

    assertThat(resultRepository.count()).isEqualTo(1);
    assertThat(outboxEventRepository.count()).isEqualTo(1);
  }

  @Test
  void rollsBackResultWhenOutboxEventCannotBePersisted() {
    assertThatThrownBy(() -> resultOutboxPersistenceAdapter.persist(
      newEnrichmentResult(MESSAGE_ID),
      newOutboxEvent(null)
    ))
      .isInstanceOf(RuntimeException.class);

    assertThat(resultRepository.count()).isZero();
    assertThat(outboxEventRepository.count()).isZero();
  }

  @Test
  void findsOnlyPendingOutboxEventsReadyForPublishing() {
    var now = CREATED_AT.plus(10, ChronoUnit.MINUTES);
    outboxEventRepository.save(OutboxEventEntity.builder()
      .messageId(MESSAGE_ID)
      .payload("ready")
      .nextAttemptAt(now.minusSeconds(1))
      .createdAt(CREATED_AT)
      .build());
    outboxEventRepository.save(OutboxEventEntity.builder()
      .messageId(UUID.randomUUID())
      .payload("future")
      .nextAttemptAt(now.plusSeconds(1))
      .createdAt(CREATED_AT)
      .build());
    outboxEventRepository.save(OutboxEventEntity.builder()
      .messageId(UUID.randomUUID())
      .payload("published")
      .status(OutboxEventStatus.PUBLISHED)
      .nextAttemptAt(now.minusSeconds(1))
      .createdAt(CREATED_AT)
      .build());

    assertThat(outboxEventRepository.findReadyForPublishing(OutboxEventStatus.PENDING, now))
      .extracting("payload", "status")
      .containsExactly(tuple("ready", OutboxEventStatus.PENDING));
  }

  private EnrichmentResult newEnrichmentResult(UUID messageId) {
    return new EnrichmentResult(messageId, 12345678L, "request", true, EVENT_TIMESTAMP, CREATED_AT);
  }

  private OutboxEvent newOutboxEvent(String payload) {
    return new OutboxEvent(MESSAGE_ID, payload, CREATED_AT, CREATED_AT);
  }

  private boolean tableExists(String tableName) {
    var query = """
      SELECT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = ?
      )
      """;

    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(query, Boolean.class, tableName));
  }

  private boolean indexExists(String indexName) {
    var query = "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = ?)";

    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(query, Boolean.class, indexName));
  }
}
