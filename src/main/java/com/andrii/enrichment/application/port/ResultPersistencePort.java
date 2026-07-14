package com.andrii.enrichment.application.port;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.domain.model.PersistedEnrichmentResult;
import java.util.Optional;
import java.util.UUID;

public interface ResultPersistencePort {

  PersistedEnrichmentResult save(EnrichmentResult enrichmentResult);

  Optional<PersistedEnrichmentResult> findByMessageId(UUID messageId);
}
