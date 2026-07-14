package com.andrii.enrichment.infrastructure.configuration;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "enrichment")
public record EnrichmentProperties(@DefaultValue("UTC") ZoneId eventTimeZone) {
}
