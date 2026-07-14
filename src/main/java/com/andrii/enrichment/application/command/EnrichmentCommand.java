package com.andrii.enrichment.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public record EnrichmentCommand(
  @NotNull UUID messageId,
  @Positive long userId,
  @NotBlank String action,
  @NotNull Instant eventTimestamp
) {
}
