package com.andrii.enrichment.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventStatus;
import com.andrii.enrichment.infrastructure.persistence.entity.ResultEntity;
import com.andrii.enrichment.infrastructure.persistence.repository.OutboxEventRepository;
import com.andrii.enrichment.infrastructure.persistence.repository.ResultRepository;
import com.andrii.enrichment.infrastructure.support.AbstractPostgresIntegrationTest;
import com.andrii.enrichment.infrastructure.support.RabbitMqTestSupport;
import com.andrii.enrichment.infrastructure.support.WireMockTestSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("integration-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class EnrichmentFlowIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String DEAD_LETTER_QUEUE = "enrichment.dlq";
  private static final String ENRICH_PATH = "/enrich";
  private static final String INPUT_EXCHANGE = "enrichment.input";
  private static final String INPUT_QUEUE = "enrichment.input.queue";
  private static final String INPUT_ROUTING_KEY = "enrichment.input";
  private static final String OUTPUT_EXCHANGE = "enrichment.output";
  private static final String OUTPUT_QUEUE = "enrichment.flow.output.queue";
  private static final String OUTPUT_ROUTING_KEY = "enrichment.output";
  private static final String RETRY_QUEUE = "enrichment.retry.queue";
  private static final String RETRY_SCENARIO = "temporary-api-failure";
  private static final String RESULT_REJECTION_FUNCTION = "reject_enrichment_result";
  private static final String RESULT_REJECTION_TRIGGER = "reject_enrichment_result_trigger";
  private static final long USER_ID = 42L;
  private static final WireMockServer WIRE_MOCK = WireMockTestSupport.startServer();

  @Container
  private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private RabbitAdmin rabbitAdmin;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private ResultRepository resultRepository;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    RabbitMqTestSupport.registerContainerProperties(registry, RABBIT_MQ);
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> true);
    registry.add("enrichment.client.base-url", WIRE_MOCK::baseUrl);
    registry.add("enrichment.client.circuit-breaker.minimum-number-of-calls", () -> 10);
    registry.add("enrichment.outbox.publisher-enabled", () -> true);
    registry.add("enrichment.outbox.publish-delay", () -> "PT0.1S");
    registry.add("enrichment.outbox.initial-backoff", () -> "PT0.1S");
    registry.add("enrichment.outbox.max-backoff", () -> "PT0.1S");
    registry.add("enrichment.outbox.max-attempts", () -> 100);
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK.stop();
  }

  @AfterEach
  void removeDatabaseFailureTrigger() {
    jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + RESULT_REJECTION_TRIGGER + " ON result");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + RESULT_REJECTION_FUNCTION + "()");
  }

  @BeforeEach
  void setUp() {
    WIRE_MOCK.resetAll();
    outboxEventRepository.deleteAll();
    resultRepository.deleteAll();
    RabbitMqTestSupport.purgeQueues(rabbitAdmin, INPUT_QUEUE, RETRY_QUEUE, DEAD_LETTER_QUEUE);
    declareOutputRoute();
    RabbitMqTestSupport.purgeQueues(rabbitAdmin, OUTPUT_QUEUE);
  }

  @Test
  void processesMessageFromInputToPublishedOutputEvent() {
    var messageId = UUID.randomUUID();
    stubSuccess();

    publish(messageId);

    var result = awaitResult(messageId);
    var output = awaitOutputMessage();

    WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo(ENRICH_PATH))
      .withRequestBody(equalToJson("{\"user_id\":42,\"action\":\"request\"}")));
    assertThat(result).extracting("messageId", "userId", "action", "result")
      .containsExactly(messageId, USER_ID, "request", true);
    assertThat(output.get("log_id").asLong()).isEqualTo(result.getId());
    assertThat(output.get("message_id").asText()).isEqualTo(messageId.toString());
    assertThat(output.get("result").asBoolean()).isTrue();
    awaitPublished(messageId);
  }

  @Test
  void ignoresRepeatedDeliveryOfProcessedMessage() {
    var messageId = UUID.randomUUID();
    stubSuccess();
    publish(messageId);
    awaitOutputMessage();

    publish(messageId);

    Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
      .untilAsserted(() -> assertThat(resultRepository.count()).isEqualTo(1));
    assertThat(outboxEventRepository.count()).isEqualTo(1);
    assertThat(rabbitTemplate.receive(OUTPUT_QUEUE, 200)).isNull();
    WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo(ENRICH_PATH)));
  }

  @Test
  void retriesTransientApiFailureAndCompletesFlowAfterRecovery() {
    var messageId = UUID.randomUUID();
    WIRE_MOCK.stubFor(post(ENRICH_PATH).inScenario(RETRY_SCENARIO).whenScenarioStateIs(Scenario.STARTED)
      .willSetStateTo("recovered").willReturn(jsonResponse(500, "{\"code\":\"unavailable\"}")));
    WIRE_MOCK.stubFor(post(ENRICH_PATH).inScenario(RETRY_SCENARIO).whenScenarioStateIs("recovered")
      .willReturn(jsonResponse(200, "{\"user_id\":42,\"result\":true}")));

    publish(messageId);

    assertThat(awaitResult(messageId).isResult()).isTrue();
    assertThat(awaitOutputMessage().get("message_id").asText()).isEqualTo(messageId.toString());
    WIRE_MOCK.verify(2, postRequestedFor(urlEqualTo(ENRICH_PATH)));
    awaitPublished(messageId);
  }

  @Test
  void routesNonRetryableApiFailureAndMalformedInputToDeadLetterQueue() {
    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(400, "{\"code\":\"invalid\"}")));
    publish(UUID.randomUUID());
    publishRaw("{invalid-json");

    var first = awaitDeadLetterMessage();
    var second = awaitDeadLetterMessage();

    assertDeadLetterPayload(first);
    assertDeadLetterPayload(second);
    assertThat(resultRepository.count()).isZero();
    assertThat(outboxEventRepository.count()).isZero();
  }

  @Test
  void retriesDatabaseTransactionFailureWithoutPersistingPartialState() {
    var messageId = UUID.randomUUID();
    stubSuccess();
    rejectResultInserts();

    publish(messageId);

    assertThat(awaitDeadLetterMessage()).isNotNull();
    assertThat(resultRepository.count()).isZero();
    assertThat(outboxEventRepository.count()).isZero();
    WIRE_MOCK.verify(4, postRequestedFor(urlEqualTo(ENRICH_PATH)));
  }

  @Test
  void retainsOutboxEventUntilOutputRouteIsRestored() {
    var messageId = UUID.randomUUID();
    stubSuccess();
    rabbitAdmin.deleteQueue(OUTPUT_QUEUE);

    publish(messageId);
    awaitResult(messageId);
    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(outboxEventRepository.findAll())
      .singleElement().satisfies(event -> {
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isPositive();
      }));
    declareOutputRoute();

    assertThat(awaitOutputMessage().get("message_id").asText()).isEqualTo(messageId.toString());
    awaitPublished(messageId);
  }

  private void stubSuccess() {
    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, "{\"user_id\":42,\"result\":true}")));
  }

  private ResponseDefinitionBuilder jsonResponse(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
  }

  private void publish(UUID messageId) {
    publishRaw("{\"message_id\":\"" + messageId + "\",\"user_id\":42,\"action\":\"request\","
      + "\"timestamp\":\"2026-07-01 10:00:00.0\"}");
  }

  private void publishRaw(String payload) {
    var properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    var message = new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    rabbitTemplate.send(INPUT_EXCHANGE, INPUT_ROUTING_KEY, message);
  }

  private void assertDeadLetterPayload(Message message) {
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
      .satisfiesAnyOf(
        body -> assertThat(body).contains("message_id"),
        body -> assertThat(body).isEqualTo("{invalid-json")
      );
  }

  private void rejectResultInserts() {
    jdbcTemplate.execute("""
      CREATE FUNCTION reject_enrichment_result() RETURNS trigger AS $$
      BEGIN
        RAISE EXCEPTION 'simulated result persistence failure';
      END;
      $$ LANGUAGE plpgsql
      """);
    jdbcTemplate.execute("""
      CREATE TRIGGER reject_enrichment_result_trigger
      BEFORE INSERT ON result
      FOR EACH ROW EXECUTE FUNCTION reject_enrichment_result()
      """);
  }

  private ResultEntity awaitResult(UUID messageId) {
    var result = new ResultEntity[1];
    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      result[0] = resultRepository.findByMessageId(messageId).orElse(null);
      assertThat(result[0]).isNotNull();
    });
    return result[0];
  }

  private JsonNode awaitOutputMessage() {
    var message = new Message[1];
    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      message[0] = rabbitTemplate.receive(OUTPUT_QUEUE, 100);
      assertThat(message[0]).isNotNull();
    });
    return objectMapper.readTree(message[0].getBody());
  }

  private Message awaitDeadLetterMessage() {
    var message = new Message[1];
    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
      message[0] = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 100);
      assertThat(message[0]).isNotNull();
    });
    return message[0];
  }

  private void awaitPublished(UUID messageId) {
    Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(outboxEventRepository.findAll())
      .filteredOn(event -> event.getMessageId().equals(messageId))
      .singleElement()
      .satisfies(event -> {
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
      }));
  }

  private void declareOutputRoute() {
    var exchange = new DirectExchange(OUTPUT_EXCHANGE);
    var queue = new Queue(OUTPUT_QUEUE, true);
    rabbitAdmin.declareExchange(exchange);
    rabbitAdmin.declareQueue(queue);
    rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(OUTPUT_ROUTING_KEY));
  }
}
