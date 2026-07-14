package com.andrii.enrichment.infrastructure.persistence.mapper;

import com.andrii.enrichment.domain.model.OutboxEvent;
import com.andrii.enrichment.infrastructure.persistence.entity.OutboxEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OutboxEventEntityMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "attemptCount", ignore = true)
  @Mapping(target = "publishedAt", ignore = true)
  OutboxEventEntity toEntity(OutboxEvent outboxEvent);
}
