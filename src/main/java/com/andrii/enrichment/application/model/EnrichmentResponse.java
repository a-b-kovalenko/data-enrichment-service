package com.andrii.enrichment.application.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EnrichmentResponse(
  @NotNull @Positive Long userId,
  @NotNull Boolean result
) {
}
