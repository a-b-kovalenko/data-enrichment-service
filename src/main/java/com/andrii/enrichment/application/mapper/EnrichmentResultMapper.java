package com.andrii.enrichment.application.mapper;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.application.model.EnrichmentResponse;
import com.andrii.enrichment.domain.model.EnrichmentResult;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EnrichmentResultMapper {

  @Mapping(target = "messageId", source = "command.messageId")
  @Mapping(target = "userId", source = "command.userId")
  @Mapping(target = "action", source = "command.action")
  @Mapping(target = "eventTimestamp", source = "command.eventTimestamp")
  @Mapping(target = "result", source = "response.result")
  EnrichmentResult toResult(EnrichmentCommand command, EnrichmentResponse response, Instant createdAt);
}
