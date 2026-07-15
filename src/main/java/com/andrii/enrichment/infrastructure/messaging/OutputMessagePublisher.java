package com.andrii.enrichment.infrastructure.messaging;

import java.util.UUID;

public interface OutputMessagePublisher {

  void publish(UUID messageId, String payload);
}
