package com.andrii.enrichment.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enrichment.outbox")
public record OutboxPublisherProperties(
  String outputExchange,
  String outputRoutingKey,
  int batchSize,
  Duration publishDelay,
  Duration confirmTimeout,
  Duration claimDuration,
  Duration initialBackoff,
  Duration maxBackoff,
  int maxAttempts
) {
}
