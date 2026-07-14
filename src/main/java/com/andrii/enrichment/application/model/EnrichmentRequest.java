package com.andrii.enrichment.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EnrichmentRequest(
  @NotNull @Positive Long userId,
  @NotBlank String action
) {
}
