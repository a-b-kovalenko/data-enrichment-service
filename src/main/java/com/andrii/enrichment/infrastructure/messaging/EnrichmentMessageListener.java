package com.andrii.enrichment.infrastructure.messaging;

import com.andrii.enrichment.application.service.EnrichmentContractService;
import com.andrii.enrichment.application.service.EnrichmentService;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrichmentMessageListener {

  EnrichmentContractService enrichmentContractService;
  EnrichmentService enrichmentService;

  @RabbitListener(queues = "${enrichment.messaging.input-queue}", errorHandler = "enrichmentListenerErrorHandler")
  public void consume(@Valid IncomingMessage incomingMessage) {
    log.info("Consuming enrichment messageId={}", incomingMessage.messageId());

    var command = enrichmentContractService.prepareCommand(incomingMessage);
    enrichmentService.enrich(command);
  }
}
