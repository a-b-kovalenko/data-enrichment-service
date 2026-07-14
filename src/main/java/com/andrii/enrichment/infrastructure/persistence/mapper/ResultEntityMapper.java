package com.andrii.enrichment.infrastructure.persistence.mapper;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.infrastructure.persistence.entity.ResultEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResultEntityMapper {

  @Mapping(target = "id", ignore = true)
  ResultEntity toEntity(EnrichmentResult enrichmentResult);

  EnrichmentResult toDomain(ResultEntity resultEntity);
}
