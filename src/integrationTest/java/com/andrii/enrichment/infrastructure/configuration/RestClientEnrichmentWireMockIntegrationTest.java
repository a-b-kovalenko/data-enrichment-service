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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestClientEnrichmentWireMockIntegrationTest {

  private static final String ENRICH_PATH = "/enrich";
  private static final String RESPONSE_JSON = "{\"user_id\":42,\"result\":true}";

  private WireMockServer wireMockServer;
  private RestClientEnrichmentAdapter adapter;
  private CircuitBreaker circuitBreaker;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();

    var circuitBreakerProperties = new EnrichmentClientProperties.CircuitBreaker(
      2, 2, 50, Duration.ofMillis(50), 1);
    var properties = new EnrichmentClientProperties(
      URI.create(wireMockServer.baseUrl()), Duration.ofSeconds(1), Duration.ofSeconds(1), circuitBreakerProperties);
    var configuration = new EnrichmentConfiguration();
    circuitBreaker = configuration.enrichmentApiCircuitBreaker(properties);
    adapter = new RestClientEnrichmentAdapter(
      configuration.enrichmentRestClient(properties), circuitBreaker);
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void enrichPostsSnakeCaseContractAndMapsSuccessResponse() {
    wireMockServer.stubFor(post(ENRICH_PATH)
      .withRequestBody(equalToJson("{\"user_id\":42,\"action\":\"request\"}"))
      .willReturn(jsonResponse(200, RESPONSE_JSON)));

    var response = adapter.enrich(request());

    assertThat(response)
      .extracting("userId", "result")
      .containsExactly(42L, true);
    wireMockServer.verify(postRequestedFor(urlEqualTo(ENRICH_PATH)));
  }

  @Test
  void enrichClassifiesHttp4xxAndMalformedResponseAsNonRetryable() {
    wireMockServer.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(400, "{\"code\":\"invalid\"}")));

    assertThatThrownBy(() -> adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class);

    wireMockServer.resetAll();
    wireMockServer.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, "{\"user_id\":42}")));

    assertThatThrownBy(() -> adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  void enrichClassifiesReadTimeoutAsRetryable() {
    wireMockServer.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, RESPONSE_JSON).withFixedDelay(200)));

    assertThatThrownBy(() -> adapter(Duration.ofMillis(25)).enrich(request()))
      .isInstanceOf(RetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API is unavailable");
  }

  @Test
  void enrichOpensCircuitForHttp5xxAndClosesItAfterHalfOpenSuccess() {
    wireMockServer.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(500, "{\"code\":\"failed\"}")));

    assertThatThrownBy(() -> adapter.enrich(request())).isInstanceOf(RetryableEnrichmentClientException.class);
    assertThatThrownBy(() -> adapter.enrich(request())).isInstanceOf(RetryableEnrichmentClientException.class);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    wireMockServer.resetAll();
    assertThatThrownBy(() -> adapter.enrich(request())).isInstanceOf(RetryableEnrichmentClientException.class);
    wireMockServer.verify(0, postRequestedFor(urlEqualTo(ENRICH_PATH)));

    wireMockServer.stubFor(post(ENRICH_PATH).willReturn(jsonResponse(200, RESPONSE_JSON)));

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
      URI.create(wireMockServer.baseUrl()), Duration.ofSeconds(1), readTimeout, circuitBreakerProperties);
    var configuration = new EnrichmentConfiguration();

    return new RestClientEnrichmentAdapter(
      configuration.enrichmentRestClient(properties),
      configuration.enrichmentApiCircuitBreaker(properties));
  }

  private ResponseDefinitionBuilder jsonResponse(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
  }
}
