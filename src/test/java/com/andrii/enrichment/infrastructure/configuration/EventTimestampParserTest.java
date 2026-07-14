package com.andrii.enrichment.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class EventTimestampParserTest {

  private static final String INVALID_TIMESTAMP = "2026-07-01";
  private static final String TIMESTAMP = "2026-07-01 10:00:00.0";

  private final EventTimestampParser eventTimestampParser = new EventTimestampParser(ZoneOffset.UTC);

  @Test
  void parsesTimestampInConfiguredTimeZone() {
    var timestamp = eventTimestampParser.parse(TIMESTAMP);

    assertThat(timestamp).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));
  }

  @Test
  void rejectsTimestampOutsideTheContractFormat() {
    assertThatThrownBy(() -> eventTimestampParser.parse(INVALID_TIMESTAMP))
      .isInstanceOf(DateTimeParseException.class);
  }
}
