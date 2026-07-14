package com.andrii.enrichment.domain.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
  UUID messageId,
  String payload,
  Instant nextAttemptAt,
  Instant createdAt
) {
}
