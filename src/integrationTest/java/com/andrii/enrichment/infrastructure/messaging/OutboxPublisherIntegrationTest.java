package com.andrii.enrichment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.andrii.enrichment.infrastructure.configuration.OutboxPublisherProperties;
import com.andrii.enrichment.infrastructure.messaging.impl.OutboxPublisherImpl;
import com.andrii.enrichment.infrastructure.persistence.OutboxEventClaimService;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventStatus;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.support.AbstractPostgresIntegrationTest;
import com.andrii.enrichment.infrastructure.support.RabbitMqTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class OutboxPublisherIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-07-01T10:00:00Z");
  private static final String OUTPUT_EXCHANGE = "enrichment.output";
  private static final String OUTPUT_QUEUE = "enrichment.output.queue";
  private static final String OUTPUT_ROUTING_KEY = "enrichment.output";

  @Container
  private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired
  private OutboxEventClaimService outboxEventClaimService;

  @Autowired
  private OutputMessagePublisher outputMessagePublisher;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private OutboxPublisherProperties outboxPublisherProperties;

  @Autowired
  private RabbitAdmin rabbitAdmin;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    RabbitMqTestSupport.registerContainerProperties(registry, RABBIT_MQ);
  }

  @BeforeEach
  void setUp() {
    outboxEventRepository.deleteAll();
    var exchange = new DirectExchange(OUTPUT_EXCHANGE);
    var queue = new Queue(OUTPUT_QUEUE, true);
    rabbitAdmin.declareExchange(exchange);
    rabbitAdmin.declareQueue(queue);
    rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(OUTPUT_ROUTING_KEY));
    RabbitMqTestSupport.purgeQueues(rabbitAdmin, OUTPUT_QUEUE);
  }

  @Test
  void publishesPendingEventAndMarksItPublishedAfterBrokerConfirm() {
    var event = outboxEventRepository.save(OutboxEventEntity.builder()
      .messageId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
      .payload("{\"log_id\":1,\"message_id\":\"123e4567-e89b-12d3-a456-426614174000\",\"result\":true}")
      .nextAttemptAt(NOW.minusSeconds(1))
      .createdAt(NOW)
      .build());
    var publisher = new OutboxPublisherImpl(outboxEventClaimService, outputMessagePublisher, outboxPublisherProperties,
      Clock.fixed(NOW, ZoneOffset.UTC));

    publisher.publishPendingEvents();

    var message = rabbitTemplate.receive(OUTPUT_QUEUE, 2_000);
    assertThat(message).isNotNull();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(event.getPayload());
    assertThat(outboxEventRepository.findById(event.getId()))
      .isPresent()
      .get()
      .extracting("status", "publishedAt")
      .containsExactly(OutboxEventStatus.PUBLISHED, NOW);
  }

  @Test
  void retainsUnroutableEventAndPublishesItAfterRouteRecovery() {
    var event = outboxEventRepository.save(OutboxEventEntity.builder()
      .messageId(UUID.randomUUID())
      .payload("{\"log_id\":1,\"message_id\":\"123e4567-e89b-12d3-a456-426614174000\",\"result\":true}")
      .nextAttemptAt(NOW.minusSeconds(1))
      .createdAt(NOW)
      .build());
    rabbitAdmin.deleteQueue(OUTPUT_QUEUE);

    publisher(NOW).publishPendingEvents();

    assertThat(outboxEventRepository.findById(event.getId()))
      .isPresent()
      .get()
      .extracting("status", "attemptCount")
      .containsExactly(OutboxEventStatus.PENDING, 1);
    declareOutputRoute();

    publisher(NOW.plusSeconds(5)).publishPendingEvents();

    assertThat(rabbitTemplate.receive(OUTPUT_QUEUE, 2_000)).isNotNull();
    assertThat(outboxEventRepository.findById(event.getId()))
      .isPresent()
      .get()
      .extracting("status", "attemptCount")
      .containsExactly(OutboxEventStatus.PUBLISHED, 1);
  }

  private OutboxPublisherImpl publisher(Instant now) {
    return new OutboxPublisherImpl(outboxEventClaimService, outputMessagePublisher, outboxPublisherProperties,
      Clock.fixed(now, ZoneOffset.UTC));
  }

  private void declareOutputRoute() {
    var exchange = new DirectExchange(OUTPUT_EXCHANGE);
    var queue = new Queue(OUTPUT_QUEUE, true);
    rabbitAdmin.declareExchange(exchange);
    rabbitAdmin.declareQueue(queue);
    rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(OUTPUT_ROUTING_KEY));
  }
}
