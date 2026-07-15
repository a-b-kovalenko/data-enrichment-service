package com.andrii.enrichment.infrastructure.support;

import org.testcontainers.containers.PostgreSQLContainer;

final class SharedPostgresContainer {

  private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    INSTANCE.start();
  }

  private SharedPostgresContainer() {
  }

  static PostgreSQLContainer<?> instance() {
    return INSTANCE;
  }
}
