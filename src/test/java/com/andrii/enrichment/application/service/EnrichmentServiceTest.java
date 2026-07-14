package com.andrii.enrichment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.exception.DuplicateMessageException;
import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import com.andrii.enrichment.application.port.ExternalEnrichmentPort;
import com.andrii.enrichment.application.port.ResultOutboxPersistencePort;
import com.andrii.enrichment.application.port.ResultPersistencePort;
import com.andrii.enrichment.application.service.impl.EnrichmentServiceImpl;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
  private static final Instant NOW = Instant.parse("2026-07-01T10:00:00Z");

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @Mock ResultPersistencePort resultPersistencePort;
  @Mock ResultOutboxPersistencePort resultOutboxPersistencePort;
  @Mock ExternalEnrichmentPort externalEnrichmentPort;
  @Mock EnrichmentContractService enrichmentContractService;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidatorFactory() {
    validatorFactory.close();
  }

  @Test
  void enrichReturnsExistingResultWithoutCallingExternalApi() {
    var command = command();
    var existing = new PersistedEnrichmentResult(7L, result());
    var service = service();
    when(resultPersistencePort.findByMessageId(MESSAGE_ID)).thenReturn(Optional.of(existing));

    var result = service.enrich(command);

    assertThat(result).extracting("logId").isEqualTo(7L);
    verifyNoInteractions(externalEnrichmentPort, resultOutboxPersistencePort);
  }

  @Test
  void enrichCallsExternalApiBeforeAtomicPersistence() {
    var command = command();
    var request = new EnrichmentRequest(42L, "request");
    var response = new EnrichmentResponse(42L, true);
    var enrichmentResult = result();
    var persisted = new PersistedEnrichmentResult(8L, enrichmentResult);
    var service = service();
    when(resultPersistencePort.findByMessageId(MESSAGE_ID)).thenReturn(Optional.empty());
    when(enrichmentContractService.prepareRequest(command)).thenReturn(request);
    when(externalEnrichmentPort.enrich(request)).thenReturn(response);
    when(enrichmentContractService.prepareResult(command, response, NOW)).thenReturn(enrichmentResult);
    when(resultOutboxPersistencePort.persist(enrichmentResult)).thenReturn(persisted);

    var result = service.enrich(command);

    assertThat(result).extracting("logId", "enrichmentResult.result").containsExactly(8L, true);
    verify(resultOutboxPersistencePort).persist(enrichmentResult);
    InOrder order = inOrder(externalEnrichmentPort, resultOutboxPersistencePort);
    order.verify(externalEnrichmentPort).enrich(request);
    order.verify(resultOutboxPersistencePort).persist(enrichmentResult);
  }

  @ParameterizedTest
  @MethodSource("enrichmentResponses")
  void enrichPersistsBothExternalResultValues(EnrichmentResponse response) {
    var command = command();
    var request = new EnrichmentRequest(42L, "request");
    var enrichmentResult = new EnrichmentResult(MESSAGE_ID, 42L, "request", response.result(), NOW, NOW);
    var service = service();
    when(resultPersistencePort.findByMessageId(MESSAGE_ID)).thenReturn(Optional.empty());
    when(enrichmentContractService.prepareRequest(command)).thenReturn(request);
    when(externalEnrichmentPort.enrich(request)).thenReturn(response);
    when(enrichmentContractService.prepareResult(command, response, NOW)).thenReturn(enrichmentResult);
    when(resultOutboxPersistencePort.persist(enrichmentResult))
      .thenReturn(new PersistedEnrichmentResult(8L, enrichmentResult));

    var result = service.enrich(command);

    assertThat(result).extracting("enrichmentResult.result").isEqualTo(response.result());
  }

  @ParameterizedTest
  @MethodSource("clientFailures")
  void enrichPropagatesClientFailureWithoutPersistence(RuntimeException failure) {
    var command = command();
    var request = new EnrichmentRequest(42L, "request");
    var service = service();
    when(resultPersistencePort.findByMessageId(MESSAGE_ID)).thenReturn(Optional.empty());
    when(enrichmentContractService.prepareRequest(command)).thenReturn(request);
    when(externalEnrichmentPort.enrich(request)).thenThrow(failure);

    assertThatThrownBy(() -> service.enrich(command)).isSameAs(failure);
    verifyNoInteractions(resultOutboxPersistencePort);
  }

  @Test
  void enrichPropagatesConcurrentDuplicateWithoutSecondOutboxEvent() {
    var command = command();
    var request = new EnrichmentRequest(42L, "request");
    var response = new EnrichmentResponse(42L, true);
    var enrichmentResult = result();
    var failure = new DuplicateMessageException(MESSAGE_ID, new RuntimeException());
    var service = service();
    when(resultPersistencePort.findByMessageId(MESSAGE_ID)).thenReturn(Optional.empty());
    when(enrichmentContractService.prepareRequest(command)).thenReturn(request);
    when(externalEnrichmentPort.enrich(request)).thenReturn(response);
    when(enrichmentContractService.prepareResult(command, response, NOW)).thenReturn(enrichmentResult);
    when(resultOutboxPersistencePort.persist(enrichmentResult)).thenThrow(failure);

    assertThatThrownBy(() -> service.enrich(command)).isSameAs(failure);
    verify(resultOutboxPersistencePort).persist(enrichmentResult);
  }

  @Test
  void enrichPropagatesPersistenceFailure() {
    var command = command();
    var request = new EnrichmentRequest(42L, "request");
    var response = new EnrichmentResponse(42L, true);
    var enrichmentResult = result();
    var failure = new RuntimeException("database unavailable");
    var service = service();
    when(resultPersistencePort.findByMessageId(MESSAGE_ID)).thenReturn(Optional.empty());
    when(enrichmentContractService.prepareRequest(command)).thenReturn(request);
    when(externalEnrichmentPort.enrich(request)).thenReturn(response);
    when(enrichmentContractService.prepareResult(command, response, NOW)).thenReturn(enrichmentResult);
    when(resultOutboxPersistencePort.persist(enrichmentResult)).thenThrow(failure);

    assertThatThrownBy(() -> service.enrich(command)).isSameAs(failure);
  }

  @ParameterizedTest
  @MethodSource("invalidCommands")
  void enrichRejectsInvalidCommandBeforeAnySideEffect(EnrichmentCommand command) {
    var service = service();

    assertThatThrownBy(() -> service.enrich(command)).isInstanceOf(ConstraintViolationException.class);
    verifyNoInteractions(resultPersistencePort, externalEnrichmentPort);
  }

  @Test
  void enrichRejectsNullCommandBeforeAnySideEffect() {
    var service = service();

    assertThatNullPointerException().isThrownBy(() -> service.enrich(null));
    verifyNoInteractions(resultPersistencePort);
  }

  private EnrichmentService service() {
    return new EnrichmentServiceImpl(resultPersistencePort, resultOutboxPersistencePort, externalEnrichmentPort,
      enrichmentContractService, clock, validator);
  }

  private EnrichmentCommand command() {
    return new EnrichmentCommand(MESSAGE_ID, 42L, "request", NOW);
  }

  @SuppressWarnings("DataFlowIssue")
  private static Stream<EnrichmentCommand> invalidCommands() {
    return Stream.of(
      new EnrichmentCommand(MESSAGE_ID, 0L, "request", NOW),
      new EnrichmentCommand(MESSAGE_ID, 42L, "", NOW),
      new EnrichmentCommand(MESSAGE_ID, 42L, null, NOW)
    );
  }

  private static Stream<EnrichmentResponse> enrichmentResponses() {
    return Stream.of(new EnrichmentResponse(42L, true), new EnrichmentResponse(42L, false));
  }

  private static Stream<RuntimeException> clientFailures() {
    return Stream.of(
      new RetryableEnrichmentClientException("retry"),
      new NonRetryableEnrichmentClientException("non-retry")
    );
  }

  private EnrichmentResult result() {
    return new EnrichmentResult(MESSAGE_ID, 42L, "request", true, NOW, NOW);
  }
}
