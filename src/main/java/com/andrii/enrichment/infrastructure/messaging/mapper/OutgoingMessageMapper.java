package com.andrii.enrichment.infrastructure.messaging.mapper;

import com.andrii.enrichment.domain.model.EnrichmentResult;
import com.andrii.enrichment.infrastructure.messaging.dto.OutgoingMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OutgoingMessageMapper {

  @Mapping(target = "logId", source = "logId")
  @Mapping(target = "messageId", source = "enrichmentResult.messageId")
  @Mapping(target = "result", source = "enrichmentResult.result")
  OutgoingMessage toOutgoingMessage(long logId, EnrichmentResult enrichmentResult);
}
