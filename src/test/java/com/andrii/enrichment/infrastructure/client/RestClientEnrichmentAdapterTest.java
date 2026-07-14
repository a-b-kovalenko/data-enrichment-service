package com.andrii.enrichment.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.andrii.enrichment.application.exception.NonRetryableEnrichmentClientException;
import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientEnrichmentAdapterTest {

  private static final String BASE_URL = "http://enrichment.example";
  private static final String ENRICH_ENDPOINT = BASE_URL + "/enrich";
  private static final String REQUEST_JSON = "{\"user_id\":42,\"action\":\"request\"}";
  private static final String SUCCESS_RESPONSE_JSON = "{\"user_id\":42,\"result\":true}";
  private static final String MISMATCHED_RESPONSE_JSON = "{\"user_id\":43,\"result\":true}";
  private static final String INCOMPLETE_RESPONSE_JSON = "{\"user_id\":42}";

  @Test
  void enrichSendsContractRequestAndMapsResponse() {
    var fixture = fixture();
    fixture.server.expect(requestTo(ENRICH_ENDPOINT))
      .andExpect(method(POST))
      .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
      .andExpect(content().json(REQUEST_JSON))
      .andRespond(withSuccess(SUCCESS_RESPONSE_JSON, APPLICATION_JSON));

    var response = fixture.adapter.enrich(request());

    assertThat(response)
      .extracting("userId", "result")
      .containsExactly(42L, true);
    fixture.server.verify();
  }

  @Test
  void enrichRejectsMismatchedUserIdWithoutRecordingAvailabilityFailure() {
    var fixture = fixture();
    fixture.server.expect(requestTo(ENRICH_ENDPOINT))
      .andRespond(withSuccess(MISMATCHED_RESPONSE_JSON, APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API returned a mismatched userId");

    assertThat(fixture.circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  void enrichRejectsIncompleteResponseWithoutRecordingAvailabilityFailure() {
    var fixture = fixture();
    fixture.server.expect(requestTo(ENRICH_ENDPOINT))
      .andRespond(withSuccess(INCOMPLETE_RESPONSE_JSON, APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API returned an incomplete response");

    assertThat(fixture.circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  void enrichRejectsMalformedResponseAsNonRetryable() {
    var fixture = fixture();
    fixture.server.expect(requestTo(ENRICH_ENDPOINT))
      .andRespond(withSuccess("{", APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API response is invalid");
  }

  @Test
  void enrichClassifiesHttp4xxAsNonRetryableWithoutOpeningCircuit() {
    var fixture = fixture();
    fixture.server.expect(requestTo(ENRICH_ENDPOINT)).andRespond(withBadRequest());

    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(NonRetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API rejected the request");

    assertThat(fixture.circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    assertThat(fixture.circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void enrichOpensCircuitAfterRetryableFailuresAndSkipsFurtherHttpCalls() {
    var fixture = fixture();
    fixture.server.expect(times(2), requestTo(ENRICH_ENDPOINT)).andRespond(withServerError());

    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(RetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API returned a server error");
    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(RetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API returned a server error");
    assertThatThrownBy(() -> fixture.adapter.enrich(request()))
      .isInstanceOf(RetryableEnrichmentClientException.class)
      .hasMessage("Enrichment API circuit is open");

    assertThat(fixture.circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    fixture.server.verify();
  }

  private Fixture fixture() {
    var restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
    var server = MockRestServiceServer.bindTo(restClientBuilder).build();
    var circuitBreaker = CircuitBreaker.of("enrichment-api", CircuitBreakerConfig.custom()
      .slidingWindowSize(2)
      .minimumNumberOfCalls(2)
      .failureRateThreshold(50)
      .waitDurationInOpenState(Duration.ofSeconds(1))
      .recordException(RetryableEnrichmentClientException.class::isInstance)
      .build());
    var adapter = new RestClientEnrichmentAdapter(
      restClientBuilder.build(), circuitBreaker);

    return new Fixture(adapter, circuitBreaker, server);
  }

  private EnrichmentRequest request() {
    return new EnrichmentRequest(42L, "request");
  }

  private record Fixture(
    RestClientEnrichmentAdapter adapter,
    CircuitBreaker circuitBreaker,
    MockRestServiceServer server
  ) {
  }
}
