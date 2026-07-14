package com.andrii.enrichment.infrastructure.messaging.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record OutgoingMessage(
  @NotNull @Positive Long logId,
  @NotNull UUID messageId,
  @NotNull Boolean result
) {
}
