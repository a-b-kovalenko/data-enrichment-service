package com.andrii.enrichment.application.mapper;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.model.EnrichmentRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EnrichmentRequestMapper {

  EnrichmentRequest toRequest(EnrichmentCommand command);
}
