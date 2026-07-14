package com.andrii.enrichment.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EnrichmentResult(
  UUID messageId,
  long userId,
  String action,
  boolean result,
  Instant eventTimestamp,
  Instant createdAt
) {
}
