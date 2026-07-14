package com.andrii.enrichment.infrastructure.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IncomingMessage(
  @NotNull UUID messageId,
  @NotNull @Positive Long userId,
  @NotBlank String action,
  @NotBlank String timestamp
) {
}
