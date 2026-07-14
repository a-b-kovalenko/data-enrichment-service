package com.andrii.enrichment.infrastructure.messaging.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import jakarta.validation.Validation;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class JsonContractTest {

  private static final String ACTION = "request";
  private static final String INCOMING_JSON = """
    {
      "message_id": "123e4567-e89b-12d3-a456-426614174000",
      "user_id": 12345678,
      "action": "request",
      "timestamp": "2026-07-01 10:00:00.0"
    }
    """;
  private static final String ENRICHMENT_REQUEST_JSON = "{\"user_id\":12345678,\"action\":\"request\"}";
  private static final String ENRICHMENT_RESPONSE_JSON = "{\"user_id\":12345678,\"result\":true}";
  private static final String OUTGOING_JSON =
    "{\"log_id\":654321,\"message_id\":\"123e4567-e89b-12d3-a456-426614174000\",\"result\":true}";
  private static final String UNKNOWN_PROPERTY_JSON = """
    {
      "message_id": "123e4567-e89b-12d3-a456-426614174000",
      "user_id": 12345678,
      "action": "request",
      "timestamp": "2026-07-01 10:00:00.0",
      "unexpected": "value"
    }
    """;
  private static final long LOG_ID = 654321L;
  private static final long USER_ID = 12345678L;
  private static final UUID MESSAGE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  private final ObjectMapper objectMapper = JsonMapper.builder()
    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build();

  @Test
  @SneakyThrows
  void deserializesIncomingSnakeCaseJson() {
    var incomingMessage = objectMapper.readValue(INCOMING_JSON, IncomingMessage.class);

    assertThat(incomingMessage.messageId()).isEqualTo(MESSAGE_ID);
    assertThat(incomingMessage.userId()).isEqualTo(USER_ID);
    assertThat(incomingMessage.action()).isEqualTo(ACTION);
  }

  @Test
  @SneakyThrows
  void serializesOutgoingSnakeCaseJson() {
    var outgoingMessage = new OutgoingMessage(LOG_ID, MESSAGE_ID, true);

    assertThat(objectMapper.writeValueAsString(outgoingMessage)).isEqualTo(OUTGOING_JSON);
  }

  @Test
  @SneakyThrows
  void serializesAndDeserializesExternalApiContractsWithSnakeCaseProperties() {
    var request = objectMapper.readValue(ENRICHMENT_REQUEST_JSON, EnrichmentRequest.class);
    var response = objectMapper.readValue(ENRICHMENT_RESPONSE_JSON, EnrichmentResponse.class);

    assertThat(objectMapper.writeValueAsString(request)).isEqualTo(ENRICHMENT_REQUEST_JSON);
    assertThat(objectMapper.writeValueAsString(response)).isEqualTo(ENRICHMENT_RESPONSE_JSON);
    assertThat(request.userId()).isEqualTo(USER_ID);
    assertThat(response.result()).isTrue();
  }

  @Test
  void rejectsUnknownJsonProperties() {
    assertThatThrownBy(() -> objectMapper.readValue(UNKNOWN_PROPERTY_JSON, IncomingMessage.class))
      .isInstanceOf(UnrecognizedPropertyException.class);
  }

  @ParameterizedTest
  @MethodSource("invalidContracts")
  void reportsMissingAndInvalidRequiredProperties(Object contract, String[] expectedProperties) {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var validator = validatorFactory.getValidator();

      assertThat(validator.validate(contract))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder(expectedProperties);
    }
  }

  @SuppressWarnings("DataFlowIssue")
  private static Stream<Arguments> invalidContracts() {
    return Stream.of(
      Arguments.of(
        new IncomingMessage(null, 0L, "", null),
        new String[] {"messageId", "userId", "action", "timestamp"}
      ),
      Arguments.of(new EnrichmentRequest(0L, ""), new String[] {"userId", "action"}),
      Arguments.of(new EnrichmentResponse(-1L, null), new String[] {"userId", "result"}),
      Arguments.of(new OutgoingMessage(0L, null, null), new String[] {"logId", "messageId", "result"})
    );
  }
}
