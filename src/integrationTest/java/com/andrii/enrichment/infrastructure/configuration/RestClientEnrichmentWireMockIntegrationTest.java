package com.andrii.enrichment.infrastructure.configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.infrastructure.client.RestClientEnrichmentAdapter;
import com.andrii.enrichment.infrastructure.support.WireMockTestSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestClientEnrichmentWireMockIntegrationTest {

  private static final String ENRICH_PATH = "/enrich";
  private static final String RESPONSE_JSON = "{\"user_id\":42,\"result\":true}";

  private static final WireMockServer WIRE_MOCK = WireMockTestSupport.startServer();

  private RestClientEnrichmentAdapter adapter;
  private CircuitBreaker circuitBreaker;

  @BeforeEach
  void setUp() {
    WIRE_MOCK.resetAll();

    var circuitBreakerProperties = new EnrichmentClientProperties.CircuitBreaker(
      2, 2, 50, Duration.ofMillis(50), 1);
    var properties = new EnrichmentClientProperties(
      URI.create(WIRE_MOCK.baseUrl()), Duration.ofSeconds(1), Duration.ofSeconds(1), circuitBreakerProperties);
    var configuration = new EnrichmentConfiguration();
    circuitBreaker = configuration.enrichmentApiCircuitBreaker(properties);
    adapter = new RestClientEnrichmentAdapter(
      configuration.enrichmentRestClient(properties), circuitBreaker);
  }

  @AfterAll
  static void tearDown() {
    WIRE_MOCK.stop();
  }

  @Test
  void enrichPostsSnakeCaseContractAndMapsSuccessResponse() {
    WIRE_MOCK.stubFor(post(ENRICH_PATH)
      .withRequestBody(equalToJson("{\"user_id\":42,\"action\":\"request\"}"))
      .willReturn(jsonResponse(200, RESPONSE_JSON)));

    var response = adapter.enrich(request());

    assertThat(response)
      .extracting("userId", "result")
      .containsExactly(42L, true);
    WIRE_MOCK.verify(postRequestedFor(urlEqualTo(ENRICH_PATH)));
  }

  @Test
  void enrichClassifiesHttp4xxAndMalformedResponseAsNonRetryable() {
    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(400, "{\"code\":\"invalid\"}")));

    assertThatThrownBy(() -> adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class);

    WIRE_MOCK.resetAll();
    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, "{\"user_id\":42}")));

    assertThatThrownBy(() -> adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  void enrichClassifiesReadTimeoutAsRetryable() {
    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, RESPONSE_JSON).withFixedDelay(200)));

    assertThatThrownBy(() -> adapter(Duration.ofMillis(25)).enrich(request()))
      .isInstanceOf(RetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API is unavailable");
  }

  @Test
  void enrichOpensCircuitForHttp5xxAndClosesItAfterHalfOpenSuccess() {
    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(500, "{\"code\":\"failed\"}")));

    assertThatThrownBy(() -> adapter.enrich(request())).isInstanceOf(RetryableEnrichmentClientException.class);
    assertThatThrownBy(() -> adapter.enrich(request())).isInstanceOf(RetryableEnrichmentClientException.class);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    WIRE_MOCK.resetAll();
    assertThatThrownBy(() -> adapter.enrich(request())).isInstanceOf(RetryableEnrichmentClientException.class);
    WIRE_MOCK.verify(0, postRequestedFor(urlEqualTo(ENRICH_PATH)));

    WIRE_MOCK.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, RESPONSE_JSON)));

    Awaitility.await()
      .atMost(Duration.ofSeconds(1))
      .untilAsserted(() -> assertThat(adapter.enrich(request()).result()).isTrue());

    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  private EnrichmentRequest request() {
    return new EnrichmentRequest(42L, "request");
  }

  private RestClientEnrichmentAdapter adapter(Duration readTimeout) {
    var circuitBreakerProperties = new EnrichmentClientProperties.CircuitBreaker(
      2, 2, 50, Duration.ofMillis(50), 1);
    var properties = new EnrichmentClientProperties(
      URI.create(WIRE_MOCK.baseUrl()), Duration.ofSeconds(1), readTimeout, circuitBreakerProperties);
    var configuration = new EnrichmentConfiguration();

    return new RestClientEnrichmentAdapter(
      configuration.enrichmentRestClient(properties),
      configuration.enrichmentApiCircuitBreaker(properties));
  }

  private ResponseDefinitionBuilder jsonResponse(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
  }
}
