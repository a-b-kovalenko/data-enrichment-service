package com.andrii.enrichment.infrastructure.messaging.impl;

import com.andrii.enrichment.infrastructure.configuration.OutboxPublisherProperties;
import com.andrii.enrichment.infrastructure.messaging.OutputMessagePublisher;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RabbitOutputMessagePublisher implements OutputMessagePublisher {

  RabbitTemplate rabbitTemplate;
  OutboxPublisherProperties properties;

  @Override
  public void publish(UUID messageId, String payload) {
    log.info("Publishing output messageId={}", messageId);
    rabbitTemplate.setMandatory(true);
    var correlationData = new CorrelationData(messageId.toString());
    rabbitTemplate.convertAndSend(
      properties.outputExchange(), properties.outputRoutingKey(), message(payload), correlationData
    );

    try {
      var confirm = correlationData.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.ack() || correlationData.getReturned() != null) {
        throw new IllegalStateException("RabbitMQ did not route output message: " + messageId);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot publish output message: " + messageId, exception);
    }
  }

  private Message message(String payload) {
    var properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
  }
}
