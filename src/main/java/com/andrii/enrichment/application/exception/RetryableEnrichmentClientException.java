package com.andrii.enrichment.application.exception;

public class RetryableEnrichmentClientException extends RuntimeException {

  public RetryableEnrichmentClientException(String message) {
    super(message);
  }

  public RetryableEnrichmentClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
