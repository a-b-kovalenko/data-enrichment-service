package com.andrii.enrichment.infrastructure.messaging;

import com.andrii.enrichment.infrastructure.configuration.EnrichmentMessagingProperties;
import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.messaging.Message;
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
  public @Nullable Object handleError(
    org.springframework.amqp.core.@NonNull Message message,
    @Nullable Channel channel,
    @Nullable Message<?> springMessage,
    @NonNull ListenerExecutionFailedException exception
  ) {
    var failure = NestedExceptionUtils.getMostSpecificCause(exception);
    var failureType = failureClassifier.classify(failure);

    if (failureType == DeliveryFailureType.NON_RETRYABLE || retryAttempts(message) >= properties.maxRetryAttempts()) {
      publishToDeadLetterQueue(message);
      return null;
    }
    throw new AmqpRejectAndDontRequeueException("Retryable enrichment delivery failure", exception);
  }

  private void publishToDeadLetterQueue(org.springframework.amqp.core.Message message) {
    log.warn("Sending enrichment message to DLQ after delivery failure");
    rabbitTemplate.send(properties.deadLetterExchange(), properties.deadLetterRoutingKey(), message);
  }

  private long retryAttempts(org.springframework.amqp.core.Message message) {
    var deaths = message.getMessageProperties().getHeader(X_DEATH_HEADER);
    if (!(deaths instanceof List<?> entries) || entries.isEmpty() || !(entries.getFirst() instanceof Map<?, ?> death)) {
      return 0;
    }
    var count = death.get(COUNT_FIELD);
    return count instanceof Number number ? number.longValue() : 0;
  }
}
