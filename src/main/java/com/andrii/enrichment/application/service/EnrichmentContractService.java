package com.andrii.enrichment.application.service;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import com.andrii.enrichment.infrastructure.messaging.dto.OutgoingMessage;
import java.time.Instant;

public interface EnrichmentContractService {

  EnrichmentCommand prepareCommand(IncomingMessage incomingMessage);

  EnrichmentRequest prepareRequest(EnrichmentCommand command);

  EnrichmentResult prepareResult(EnrichmentCommand command, EnrichmentResponse response, Instant createdAt);

  OutgoingMessage prepareOutgoingMessage(long logId, EnrichmentResult enrichmentResult);
}
