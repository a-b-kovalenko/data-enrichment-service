package com.andrii.enrichment.application.service.impl;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.mapper.EnrichmentRequestMapper;
import com.andrii.enrichment.application.mapper.EnrichmentResultMapper;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import com.andrii.enrichment.application.service.EnrichmentContractService;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.infrastructure.configuration.EventTimestampParser;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import com.andrii.enrichment.infrastructure.messaging.dto.OutgoingMessage;
import com.andrii.enrichment.infrastructure.messaging.mapper.IncomingMessageMapper;
import com.andrii.enrichment.infrastructure.messaging.mapper.OutgoingMessageMapper;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrichmentContractServiceImpl implements EnrichmentContractService {

  IncomingMessageMapper incomingMessageMapper;
  EnrichmentRequestMapper enrichmentRequestMapper;
  EnrichmentResultMapper enrichmentResultMapper;
  OutgoingMessageMapper outgoingMessageMapper;
  EventTimestampParser eventTimestampParser;

  @Override
  public EnrichmentCommand prepareCommand(IncomingMessage incomingMessage) {
    return incomingMessageMapper.toCommand(incomingMessage, eventTimestampParser);
  }

  @Override
  public EnrichmentRequest prepareRequest(EnrichmentCommand command) {
    return enrichmentRequestMapper.toRequest(command);
  }

  @Override
  public EnrichmentResult prepareResult(EnrichmentCommand command, EnrichmentResponse response, Instant createdAt) {
    return enrichmentResultMapper.toResult(command, response, createdAt);
  }

  @Override
  public OutgoingMessage prepareOutgoingMessage(long logId, EnrichmentResult enrichmentResult) {
    return outgoingMessageMapper.toOutgoingMessage(logId, enrichmentResult);
  }
}
