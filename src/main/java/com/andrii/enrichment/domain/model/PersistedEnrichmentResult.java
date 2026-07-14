package com.andrii.enrichment.domain.model;

public record PersistedEnrichmentResult(
  long logId,
  EnrichmentResult enrichmentResult
) {
}
