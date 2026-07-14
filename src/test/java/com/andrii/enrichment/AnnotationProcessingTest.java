package com.andrii.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Value;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

class AnnotationProcessingTest {

  @Test
  void mapsValueFromLombokGeneratedAccessor() {
    var source = new LombokSource("enriched");
    var target = SampleMapper.INSTANCE.toTarget(source);

    assertThat(target.value()).isEqualTo("enriched");
  }

  @Value
  static class LombokSource {
    String value;
  }

  record MapStructTarget(String value) {
  }

  @Mapper
  interface SampleMapper {
    SampleMapper INSTANCE = Mappers.getMapper(SampleMapper.class);

    MapStructTarget toTarget(LombokSource source);
  }
}
