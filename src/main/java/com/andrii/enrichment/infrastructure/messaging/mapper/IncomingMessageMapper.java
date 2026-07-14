package com.andrii.enrichment.infrastructure.messaging.mapper;

import com.andrii.enrichment.application.command.EnrichmentCommand;
import com.andrii.enrichment.infrastructure.configuration.EventTimestampParser;
import com.andrii.enrichment.infrastructure.messaging.dto.IncomingMessage;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IncomingMessageMapper {

  @Mapping(target = "eventTimestamp", expression = "java(eventTimestampParser.parse(incomingMessage.timestamp()))")
  EnrichmentCommand toCommand(IncomingMessage incomingMessage, @Context EventTimestampParser eventTimestampParser);
}
