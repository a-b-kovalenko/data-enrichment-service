package com.andrii.enrichment.infrastructure.configuration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class EventTimestampParser {

  private static final DateTimeFormatter INPUT_TIMESTAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");

  private final ZoneId zoneId;

  public EventTimestampParser(ZoneId zoneId) {
    this.zoneId = zoneId;
  }

  public Instant parse(String timestamp) {
    return LocalDateTime.parse(timestamp, INPUT_TIMESTAMP_FORMATTER)
      .atZone(zoneId)
      .toInstant();
  }
}
