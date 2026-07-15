package com.andrii.enrichment.infrastructure.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresIntegrationTest {

  protected static final PostgreSQLContainer<?> POSTGRES = SharedPostgresContainer.instance();

  @DynamicPropertySource
  static void configurePostgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
