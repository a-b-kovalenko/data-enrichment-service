package com.andrii.enrichment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.andrii.enrichment.application.mapper.EnrichmentRequestMapper;
import com.andrii.enrichment.application.mapper.EnrichmentResultMapper;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import com.andrii.enrichment.application.service.impl.EnrichmentContractServiceImpl;
import com.andrii.enrichment.infrastructure.configuration.EventTimestampParser;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import com.andrii.enrichment.infrastructure.messaging.mapper.IncomingMessageMapper;
import com.andrii.enrichment.infrastructure.messaging.mapper.OutgoingMessageMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class EnrichmentContractServiceTest {

  private static final String ACTION = "request";
  private static final Instant CREATED_AT = Instant.parse("2026-07-01T10:00:01Z");
  private static final String TIMESTAMP = "2026-07-01 10:00:00.0";
  private static final long LOG_ID = 654321L;
  private static final long USER_ID = 12345678L;
  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  private final EnrichmentContractService enrichmentContractService = new EnrichmentContractServiceImpl(
    Mappers.getMapper(IncomingMessageMapper.class),
    Mappers.getMapper(EnrichmentRequestMapper.class),
    Mappers.getMapper(EnrichmentResultMapper.class),
    Mappers.getMapper(OutgoingMessageMapper.class),
    new EventTimestampParser(ZoneOffset.UTC)
  );

  @Test
  void preparesContractsForTheEnrichmentFlow() {
    var command = enrichmentContractService.prepareCommand(
      new IncomingMessage(MESSAGE_ID, USER_ID, ACTION, TIMESTAMP)
    );
    var request = enrichmentContractService.prepareRequest(command);
    var result = enrichmentContractService.prepareResult(command, new EnrichmentResponse(USER_ID, true), CREATED_AT);
    var outgoingMessage = enrichmentContractService.prepareOutgoingMessage(LOG_ID, result);

    assertThat(command)
      .extracting("messageId", "userId", "action", "eventTimestamp")
      .containsExactly(MESSAGE_ID, USER_ID, ACTION, Instant.parse("2026-07-01T10:00:00Z"));
    assertThat(request)
      .extracting("userId", "action")
      .containsExactly(USER_ID, ACTION);
    assertThat(result)
      .extracting("createdAt")
      .isEqualTo(CREATED_AT);
    assertThat(outgoingMessage)
      .extracting("logId", "messageId", "result")
      .containsExactly(LOG_ID, MESSAGE_ID, true);
  }
}
