package com.andrii.enrichment.application.service;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;

public interface EnrichmentService {

  PersistedEnrichmentResult enrich(EnrichmentCommand command);
}
