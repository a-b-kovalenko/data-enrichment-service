package com.andrii.enrichment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataEnrichmentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(DataEnrichmentServiceApplication.class, args);
  }
}
