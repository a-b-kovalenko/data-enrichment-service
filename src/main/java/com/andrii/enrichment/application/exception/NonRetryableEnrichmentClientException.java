package com.andrii.enrichment.application.exception;

public class NonRetryableEnrichmentClientException extends RuntimeException {

  public NonRetryableEnrichmentClientException(String message) {
    super(message);
  }

  public NonRetryableEnrichmentClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
