package com.andrii.enrichment.infrastructure.messaging;

import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Component;

@Component
public class DeliveryFailureClassifier {

  @SuppressWarnings("unused")
  public DeliveryFailureType classify(Throwable exception) {
    return switch (exception) {
      case NonRetryableEnrichmentClientException nonRetryable -> DeliveryFailureType.NON_RETRYABLE;
      case ConstraintViolationException violation -> DeliveryFailureType.NON_RETRYABLE;
      case MethodArgumentNotValidException validationFailure -> DeliveryFailureType.NON_RETRYABLE;
      case MessageConversionException conversionFailure -> DeliveryFailureType.NON_RETRYABLE;
      case IllegalArgumentException illegalArgument -> DeliveryFailureType.NON_RETRYABLE;
      default -> DeliveryFailureType.RETRYABLE;
    };
  }
}
