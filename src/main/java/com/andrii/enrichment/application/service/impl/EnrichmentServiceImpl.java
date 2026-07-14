package com.andrii.enrichment.application.service.impl;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.port.ExternalEnrichmentPort;
import com.andrii.enrichment.application.port.ResultOutboxPersistencePort;
import com.andrii.enrichment.application.port.ResultPersistencePort;
import com.andrii.enrichment.application.service.EnrichmentContractService;
import com.andrii.enrichment.application.service.EnrichmentService;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrichmentServiceImpl implements EnrichmentService {

  ResultPersistencePort resultPersistencePort;
  ResultOutboxPersistencePort resultOutboxPersistencePort;
  ExternalEnrichmentPort externalEnrichmentPort;
  EnrichmentContractService enrichmentContractService;
  Clock clock;
  Validator validator;

  @Override
  public PersistedEnrichmentResult enrich(EnrichmentCommand command) {
    validate(command);
    log.info("Enriching messageId={}", command.messageId());

    return resultPersistencePort.findByMessageId(command.messageId())
      .orElseGet(() -> enrichNewCommand(command));
  }

  private PersistedEnrichmentResult enrichNewCommand(EnrichmentCommand command) {
    var response = externalEnrichmentPort.enrich(enrichmentContractService.prepareRequest(command));
    var result = enrichmentContractService.prepareResult(command, response, clock.instant());

    return resultOutboxPersistencePort.persist(result);
  }

  private void validate(EnrichmentCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
