package com.andrii.enrichment.application.exception;

import java.util.UUID;

public class DuplicateMessageException extends RuntimeException {

  public DuplicateMessageException(UUID messageId, Throwable cause) {
    super("Message was already processed: " + messageId, cause);
  }
}
