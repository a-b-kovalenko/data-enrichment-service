package com.andrii.enrichment.application.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EnrichmentResponse(
  @NotNull @Positive Long userId,
  @NotNull Boolean result
) {
}
