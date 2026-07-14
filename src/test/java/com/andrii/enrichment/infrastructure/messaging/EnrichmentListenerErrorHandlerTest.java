package com.andrii.enrichment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import com.andrii.enrichment.infrastructure.configuration.EnrichmentMessagingProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

@ExtendWith(MockitoExtension.class)
class EnrichmentListenerErrorHandlerTest {

  @Mock DeliveryFailureClassifier failureClassifier;
  @Mock EnrichmentMessagingProperties properties;
  @Mock RabbitTemplate rabbitTemplate;
  @InjectMocks EnrichmentListenerErrorHandler errorHandler;

  @Test
  void sendsNonRetryableDeliveryToDeadLetterExchange() {
    var message = new Message(new byte[0], new MessageProperties());
    var exception = new ListenerExecutionFailedException(
      "failed", new NonRetryableEnrichmentClientException("invalid"));
    when(failureClassifier.classify(exception.getCause())).thenReturn(DeliveryFailureType.NON_RETRYABLE);
    when(properties.deadLetterExchange()).thenReturn("dlx");
    when(properties.deadLetterRoutingKey()).thenReturn("dlq");

    errorHandler.handleError(message, null, null, exception);

    verify(rabbitTemplate).send("dlx", "dlq", message);
  }

  @Test
  void rejectsRetryableDeliveryForRetryQueueRouting() {
    var message = new Message(new byte[0], new MessageProperties());
    var exception = new ListenerExecutionFailedException("failed", new RetryableEnrichmentClientException("retry"));
    when(failureClassifier.classify(exception.getCause())).thenReturn(DeliveryFailureType.RETRYABLE);
    when(properties.maxRetryAttempts()).thenReturn(3);

    assertThatThrownBy(() -> errorHandler.handleError(message, null, null, exception))
      .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void rejectsRetryableDeliveryBelowAttemptLimit() {
    var message = messageWithDeaths(2L);
    var exception = new ListenerExecutionFailedException("failed", new RetryableEnrichmentClientException("retry"));
    when(failureClassifier.classify(exception.getCause())).thenReturn(DeliveryFailureType.RETRYABLE);
    when(properties.maxRetryAttempts()).thenReturn(3);

    assertThatThrownBy(() -> errorHandler.handleError(message, null, null, exception))
      .isInstanceOf(AmqpRejectAndDontRequeueException.class);
  }

  @Test
  void sendsRetryableDeliveryAtAttemptLimitToDeadLetterExchange() {
    var message = messageWithDeaths(3L);
    var exception = new ListenerExecutionFailedException("failed", new RetryableEnrichmentClientException("retry"));
    when(failureClassifier.classify(exception.getCause())).thenReturn(DeliveryFailureType.RETRYABLE);
    when(properties.maxRetryAttempts()).thenReturn(3);
    when(properties.deadLetterExchange()).thenReturn("dlx");
    when(properties.deadLetterRoutingKey()).thenReturn("dlq");

    errorHandler.handleError(message, null, null, exception);

    verify(rabbitTemplate).send("dlx", "dlq", message);
  }

  @Test
  void treatsMalformedDeathHeaderAsFirstRetryAttempt() {
    var properties = new MessageProperties();
    properties.setHeader("x-death", List.of("invalid"));
    var message = new Message(new byte[0], properties);
    var exception = new ListenerExecutionFailedException("failed", new RetryableEnrichmentClientException("retry"));
    when(failureClassifier.classify(exception.getCause())).thenReturn(DeliveryFailureType.RETRYABLE);
    when(this.properties.maxRetryAttempts()).thenReturn(3);

    assertThatThrownBy(() -> errorHandler.handleError(message, null, null, exception))
      .isInstanceOf(AmqpRejectAndDontRequeueException.class);
  }

  private Message messageWithDeaths(long count) {
    var properties = new MessageProperties();
    properties.setHeader("x-death", List.of(Map.of("count", count)));
    return new Message(new byte[0], properties);
  }
}
