package com.andrii.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntegrationTestSourceSetTest {

  @Test
  void runsFromDedicatedIntegrationTestSourceSet() {
    assertThat(true).isTrue();
  }
}
