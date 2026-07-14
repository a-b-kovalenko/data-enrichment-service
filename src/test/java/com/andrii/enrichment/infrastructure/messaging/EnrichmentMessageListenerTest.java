package com.andrii.enrichment.infrastructure.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.service.EnrichmentContractService;
import com.andrii.enrichment.application.service.EnrichmentService;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrichmentMessageListenerTest {

  @Mock EnrichmentContractService enrichmentContractService;
  @Mock EnrichmentService enrichmentService;
  @InjectMocks EnrichmentMessageListener listener;

  @Test
  void consumesMessageThroughApplicationService() {
    var messageId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    var incomingMessage = new IncomingMessage(messageId, 42L, "request", "2026-07-01 10:00:00.0");
    var command = new EnrichmentCommand(messageId, 42L, "request", Instant.parse("2026-07-01T10:00:00Z"));
    when(enrichmentContractService.prepareCommand(incomingMessage)).thenReturn(command);

    listener.consume(incomingMessage);

    verify(enrichmentService).enrich(command);
  }
}
