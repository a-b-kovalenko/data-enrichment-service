package com.andrii.enrichment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.support.converter.MessageConversionException;

class DeliveryFailureClassifierTest {

  private final DeliveryFailureClassifier classifier = new DeliveryFailureClassifier();

  @ParameterizedTest
  @MethodSource("failures")
  void classifiesDeliveryFailures(Throwable exception, DeliveryFailureType expectedType) {
    var result = classifier.classify(exception);

    assertThat(result).isEqualTo(expectedType);
  }

  private static Stream<Arguments> failures() {
    return Stream.of(
      Arguments.of(new RetryableEnrichmentClientException("retry"), DeliveryFailureType.RETRYABLE),
      Arguments.of(new NonRetryableEnrichmentClientException("non-retry"), DeliveryFailureType.NON_RETRYABLE),
      Arguments.of(new ConstraintViolationException(Set.of()), DeliveryFailureType.NON_RETRYABLE),
      Arguments.of(new MessageConversionException("invalid JSON"), DeliveryFailureType.NON_RETRYABLE),
      Arguments.of(new IllegalStateException("database"), DeliveryFailureType.RETRYABLE)
    );
  }
}
