package com.andrii.enrichment.infrastructure.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.andrii.enrichment.infrastructure.configuration.OutboxPublisherProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;

@ExtendWith(MockitoExtension.class)
class RabbitOutputMessagePublisherTest {

  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Mock
  private RabbitTemplate rabbitTemplate;

  @Test
  void publishesPayloadWithMandatoryRoutingAndBrokerConfirm() {
    var publisher = publisher();
    doAnswer(invocation -> {
      var correlationData = invocation.getArgument(3, CorrelationData.class);
      correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbitTemplate).convertAndSend(eq("output"), eq("output"), any(Message.class), any(CorrelationData.class));

    publisher.publish(MESSAGE_ID, "payload");

    var correlationData = ArgumentCaptor.forClass(CorrelationData.class);
    var message = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).setMandatory(true);
    verify(rabbitTemplate).convertAndSend(eq("output"), eq("output"), message.capture(), correlationData.capture());
    assertThat(correlationData.getValue().getId()).isEqualTo(MESSAGE_ID.toString());
    assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("payload");
  }

  @Test
  void failsWhenBrokerNacksMessage() {
    var publisher = publisher();
    doAnswer(invocation -> {
      var correlationData = invocation.getArgument(3, CorrelationData.class);
      correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
      return null;
    }).when(rabbitTemplate).convertAndSend(eq("output"), eq("output"), any(Message.class), any(CorrelationData.class));

    assertThatThrownBy(() -> publisher.publish(MESSAGE_ID, "payload"))
      .isInstanceOf(IllegalStateException.class);
  }

  private RabbitOutputMessagePublisher publisher() {
    var properties = new OutboxPublisherProperties(
      "output", "output", 20, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(30),
      Duration.ofSeconds(5), Duration.ofMinutes(5), 10
    );
    return new RabbitOutputMessagePublisher(rabbitTemplate, properties);
  }
}
