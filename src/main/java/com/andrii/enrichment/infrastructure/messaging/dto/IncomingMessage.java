package com.andrii.enrichment.infrastructure.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record IncomingMessage(
  @NotNull UUID messageId,
  @NotNull @Positive Long userId,
  @NotBlank String action,
  @NotBlank String timestamp
) {
}
