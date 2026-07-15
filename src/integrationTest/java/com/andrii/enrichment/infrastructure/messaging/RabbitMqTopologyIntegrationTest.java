package com.andrii.enrichment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.andrii.enrichment.infrastructure.configuration.EnrichmentMessagingProperties;
import com.andrii.enrichment.infrastructure.configuration.OutboxPublisherProperties;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RabbitMqTopologyIntegrationTest {

  private static final String INPUT_EXCHANGE = "enrichment.input";
  private static final String INPUT_QUEUE = "enrichment.input.queue";
  private static final String INPUT_ROUTING_KEY = "enrichment.input";

  @Container
  private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

  private RabbitAdmin rabbitAdmin;

  @BeforeEach
  void setUp() {
    var connectionFactory = new CachingConnectionFactory(RABBIT_MQ.getHost(), RABBIT_MQ.getAmqpPort());
    connectionFactory.setUsername(RABBIT_MQ.getAdminUsername());
    connectionFactory.setPassword(RABBIT_MQ.getAdminPassword());
    rabbitAdmin = new RabbitAdmin(connectionFactory);
    declareTopology();
  }

  @Test
  void routesInputMessageToInputQueue() {
    var payload = "{\"message_id\":\"123e4567-e89b-12d3-a456-426614174000\"}";

    rabbitAdmin.getRabbitTemplate().convertAndSend(INPUT_EXCHANGE, INPUT_ROUTING_KEY, payload);

    var message = rabbitAdmin.getRabbitTemplate().receive(INPUT_QUEUE, 1_000);

    assertThat(message).isNotNull();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
  }

  @Test
  void returnsRejectedInputMessageAfterRetryQueueTtl() {
    var payload = "retry";
    rabbitAdmin.getRabbitTemplate().convertAndSend(INPUT_EXCHANGE, INPUT_ROUTING_KEY, payload);
    rejectInputMessage();

    Awaitility.await()
      .atMost(Duration.ofSeconds(3))
      .untilAsserted(() -> assertThat(rabbitAdmin.getRabbitTemplate().receive(INPUT_QUEUE, 100)).isNotNull());
  }

  @Test
  void routesDeadLetterExchangeMessageToDeadLetterQueue() {
    var payload = "dead-letter";
    rabbitAdmin.getRabbitTemplate().convertAndSend("enrichment.dlx", "enrichment.dlq", payload);

    var message = rabbitAdmin.getRabbitTemplate().receive("enrichment.dlq", 1_000);

    assertThat(message).isNotNull();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
  }

  private void rejectInputMessage() {
    rabbitAdmin.getRabbitTemplate().execute(channel -> {
      var delivery = channel.basicGet(INPUT_QUEUE, false);
      channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
      return null;
    });
  }

  private void declareTopology() {
    var configuration = getEnrichmentMessagingConfiguration();

    rabbitAdmin.declareExchange(configuration.inputExchange());
    rabbitAdmin.declareExchange(configuration.retryExchange());
    rabbitAdmin.declareExchange(configuration.deadLetterExchange());
    rabbitAdmin.declareExchange(configuration.outputExchange());
    rabbitAdmin.declareQueue(configuration.inputQueue());
    rabbitAdmin.declareQueue(configuration.retryQueue());
    rabbitAdmin.declareQueue(configuration.deadLetterQueue());
    rabbitAdmin.declareBinding(configuration.inputBinding());
    rabbitAdmin.declareBinding(configuration.retryBinding());
    rabbitAdmin.declareBinding(configuration.deadLetterBinding());
  }

  private static @NonNull EnrichmentMessagingConfiguration getEnrichmentMessagingConfiguration() {
    var properties = new EnrichmentMessagingProperties(
      INPUT_EXCHANGE, INPUT_QUEUE, INPUT_ROUTING_KEY,
      "enrichment.retry", "enrichment.retry.queue", "enrichment.retry",
      "enrichment.dlx", "enrichment.dlq", "enrichment.dlq", 100, 3);
    var outboxProperties = new OutboxPublisherProperties(
      "enrichment.output", "enrichment.output", 20, Duration.ofSeconds(1), Duration.ofSeconds(5),
      Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(5), 10
    );
    var configuration = new EnrichmentMessagingConfiguration(properties, outboxProperties);
    return configuration;
  }
}
