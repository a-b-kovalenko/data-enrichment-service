package com.andrii.enrichment.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enrichment.messaging")
public record EnrichmentMessagingProperties(
  String inputExchange,
  String inputQueue,
  String inputRoutingKey,
  String retryExchange,
  String retryQueue,
  String retryRoutingKey,
  String deadLetterExchange,
  String deadLetterQueue,
  String deadLetterRoutingKey,
  int retryDelayMilliseconds,
  int maxRetryAttempts
) {
}
