package com.andrii.enrichment.application.port;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;

public interface ResultOutboxPersistencePort {

  PersistedEnrichmentResult persist(EnrichmentResult enrichmentResult);
}
