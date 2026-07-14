package com.andrii.enrichment.infrastructure.configuration;

import com.andrii.enrichment.application.exception.RetryableEnrichmentClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
  EnrichmentProperties.class,
  EnrichmentClientProperties.class,
  EnrichmentMessagingProperties.class
})
public class EnrichmentConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  EventTimestampParser eventTimestampParser(EnrichmentProperties enrichmentProperties) {
    return new EventTimestampParser(enrichmentProperties.eventTimeZone());
  }

  @Bean
  RestClient enrichmentRestClient(EnrichmentClientProperties properties) {
    return RestClient.builder()
      .baseUrl(properties.baseUrl().toString())
      .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
      .build();
  }

  @Bean
  CircuitBreaker enrichmentApiCircuitBreaker(EnrichmentClientProperties properties) {
    var configuration = properties.circuitBreaker();
    var circuitBreakerConfig = CircuitBreakerConfig.custom()
      .slidingWindowSize(configuration.slidingWindowSize())
      .minimumNumberOfCalls(configuration.minimumNumberOfCalls())
      .failureRateThreshold(configuration.failureRateThreshold())
      .waitDurationInOpenState(configuration.waitDurationInOpenState())
      .permittedNumberOfCallsInHalfOpenState(configuration.permittedNumberOfCallsInHalfOpenState())
      .recordException(RetryableEnrichmentClientException.class::isInstance)
      .build();

    return CircuitBreaker.of("enrichment-api", circuitBreakerConfig);
  }

  private JdkClientHttpRequestFactory requestFactory(
    Duration connectTimeout, Duration readTimeout
  ) {
    var httpClient = HttpClient.newBuilder()
      .connectTimeout(connectTimeout)
      .version(HttpClient.Version.HTTP_1_1)
      .build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return requestFactory;
  }
}
