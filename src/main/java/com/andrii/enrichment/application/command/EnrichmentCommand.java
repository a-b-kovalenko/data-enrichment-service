package com.andrii.enrichment.application.command;

import java.time.Instant;
import java.util.UUID;

public record EnrichmentCommand(
  UUID messageId,
  long userId,
  String action,
  Instant eventTimestamp
) {
}
