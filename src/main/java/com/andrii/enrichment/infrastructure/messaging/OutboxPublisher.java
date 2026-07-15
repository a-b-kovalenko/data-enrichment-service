package com.andrii.enrichment.infrastructure.messaging;

public interface OutboxPublisher {

  void publishPendingEvents();
}
