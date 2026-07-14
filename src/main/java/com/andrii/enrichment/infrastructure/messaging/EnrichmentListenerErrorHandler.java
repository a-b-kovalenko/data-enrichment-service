package com.andrii.enrichment.infrastructure.messaging;

import com.andrii.enrichment.infrastructure.configuration.EnrichmentMessagingProperties;
import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;

@Component("enrichmentListenerErrorHandler")
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrichmentListenerErrorHandler implements RabbitListenerErrorHandler {

  static final String X_DEATH_HEADER = "x-death";
  static final String COUNT_FIELD = "count";

  DeliveryFailureClassifier failureClassifier;
  EnrichmentMessagingProperties properties;
  RabbitTemplate rabbitTemplate;

  @Override
  public Object handleError(Message message, Channel channel, org.springframework.messaging.Message<?> springMessage,
    ListenerExecutionFailedException exception) {
    var failure = exception.getCause() == null ? exception : exception.getCause();
    var failureType = failureClassifier.classify(failure);

    if (failureType == DeliveryFailureType.NON_RETRYABLE || retryAttempts(message) >= properties.maxRetryAttempts()) {
      publishToDeadLetterQueue(message);
      return null;
    }
    throw new AmqpRejectAndDontRequeueException("Retryable enrichment delivery failure", exception);
  }

  private void publishToDeadLetterQueue(Message message) {
    log.warn("Sending enrichment message to DLQ after delivery failure");
    rabbitTemplate.send(properties.deadLetterExchange(), properties.deadLetterRoutingKey(), message);
  }

  private long retryAttempts(Message message) {
    var deaths = message.getMessageProperties().getHeader(X_DEATH_HEADER);
    if (!(deaths instanceof List<?> entries) || entries.isEmpty() || !(entries.getFirst() instanceof Map<?, ?> death)) {
      return 0;
    }
    var count = death.get(COUNT_FIELD);
    return count instanceof Number number ? number.longValue() : 0;
  }
}
