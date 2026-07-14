package com.andrii.enrichment.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EnrichmentProperties.class)
public class EnrichmentConfiguration {

  @Bean
  EventTimestampParser eventTimestampParser(EnrichmentProperties enrichmentProperties) {
    return new EventTimestampParser(enrichmentProperties.eventTimeZone());
  }
}
