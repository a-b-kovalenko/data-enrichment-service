package com.andrii.enrichment.infrastructure.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "enrichment.client")
public record EnrichmentClientProperties(
  @DefaultValue("http://localhost:8081") URI baseUrl,
  @DefaultValue("PT1S") Duration connectTimeout,
  @DefaultValue("PT2S") Duration readTimeout,
  CircuitBreaker circuitBreaker
) {

  public record CircuitBreaker(
    @DefaultValue("4") int slidingWindowSize,
    @DefaultValue("2") int minimumNumberOfCalls,
    @DefaultValue("50") float failureRateThreshold,
    @DefaultValue("PT5S") Duration waitDurationInOpenState,
    @DefaultValue("1") int permittedNumberOfCallsInHalfOpenState
  ) {
  }
}
