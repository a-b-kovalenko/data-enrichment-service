package com.andrii.enrichment.application.port;

import com.andrii.enrichment.application.model.EnrichmentRequest;
import com.andrii.enrichment.application.model.EnrichmentResponse;

public interface ExternalEnrichmentPort {

  EnrichmentResponse enrich(EnrichmentRequest request);
}
