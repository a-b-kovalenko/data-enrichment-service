package com.andrii.enrichment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import com.andrii.enrichment.application.service.EnrichmentContractService;
import com.andrii.enrichment.application.service.EnrichmentService;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import com.andrii.enrichment.infrastructure.support.AbstractPostgresIntegrationTest;
import com.andrii.enrichment.infrastructure.support.RabbitMqTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class EnrichmentMessageListenerIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String DEAD_LETTER_QUEUE = "enrichment.dlq";
  private static final String INPUT_EXCHANGE = "enrichment.input";
  private static final String INPUT_ROUTING_KEY = "enrichment.input";
  private static final UUID MESSAGE_ID = UUID.fromString("423e4567-e89b-12d3-a456-426614174000");

  @Container
  private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired
  private RabbitAdmin rabbitAdmin;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @MockitoBean
  private EnrichmentContractService enrichmentContractService;

  @MockitoBean
  private EnrichmentService enrichmentService;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    RabbitMqTestSupport.registerContainerProperties(registry, RABBIT_MQ);
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> true);
  }

  @BeforeEach
  void clearQueues() {
    RabbitMqTestSupport.purgeQueues(
      rabbitAdmin, "enrichment.input.queue", "enrichment.retry.queue", DEAD_LETTER_QUEUE
    );
  }

  @Test
  void consumesValidJsonMessageThroughApplicationServices() {
    var incomingMessage = new IncomingMessage(MESSAGE_ID, 42L, "request", "2026-07-01 10:00:00.0");
    var command = new EnrichmentCommand(MESSAGE_ID, 42L, "request", Instant.parse("2026-07-01T10:00:00Z"));
    when(enrichmentContractService.prepareCommand(incomingMessage)).thenReturn(command);

    publish("""
      {"message_id":"423e4567-e89b-12d3-a456-426614174000","user_id":42,
      "action":"request","timestamp":"2026-07-01 10:00:00.0"}
      """);

    Awaitility.await()
      .atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> verify(enrichmentService).enrich(command));
  }

  @Test
  void routesMalformedJsonToDeadLetterQueueWithoutCallingApplicationServices() {
    publish("{invalid-json");

    var deadLetterMessage = awaitDeadLetterMessage();

    assertThat(new String(deadLetterMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo("{invalid-json");
    verifyNoInteractions(enrichmentContractService, enrichmentService);
  }

  @Test
  void routesValidationFailureToDeadLetterQueueWithoutCallingApplicationServices() {
    publish("""
      {"message_id":"423e4567-e89b-12d3-a456-426614174000","user_id":0,
      "action":"","timestamp":"2026-07-01 10:00:00.0"}
      """);

    var deadLetterMessage = awaitDeadLetterMessage();

    assertThat(new String(deadLetterMessage.getBody(), StandardCharsets.UTF_8)).contains("\"user_id\":0");
    verifyNoInteractions(enrichmentContractService, enrichmentService);
  }

  @Test
  void routesOpenCircuitBreakerToDeadLetterQueueAfterConfiguredAttempts() {
    var incomingMessage = new IncomingMessage(MESSAGE_ID, 42L, "request", "2026-07-01 10:00:00.0");
    var command = new EnrichmentCommand(MESSAGE_ID, 42L, "request", Instant.parse("2026-07-01T10:00:00Z"));
    var circuitBreaker = CircuitBreaker.ofDefaults("external-enrichment");
    var exception = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
    var failure = new RetryableEnrichmentClientException("circuit open", exception);
    when(enrichmentContractService.prepareCommand(incomingMessage)).thenReturn(command);
    when(enrichmentService.enrich(command)).thenThrow(failure);

    publish("""
      {"message_id":"423e4567-e89b-12d3-a456-426614174000","user_id":42,
      "action":"request","timestamp":"2026-07-01 10:00:00.0"}
      """);

    Awaitility.await()
      .atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> verify(enrichmentService, times(4)).enrich(command));

    assertThat(awaitDeadLetterMessage()).isNotNull();
  }

  private void publish(String payload) {
    var properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    var message = new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    rabbitTemplate.send(INPUT_EXCHANGE, INPUT_ROUTING_KEY, message);
  }

  private Message awaitDeadLetterMessage() {
    var message = new Message[1];

    Awaitility.await()
      .atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> {
        message[0] = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 100);
        assertThat(message[0]).isNotNull();
      });

    return message[0];
  }
}
