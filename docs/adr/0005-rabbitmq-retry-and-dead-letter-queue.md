# 0005. RabbitMQ retry and dead-letter queue policy

## Context

An input delivery can fail because of a temporary dependency outage, an open Circuit Breaker, malformed JSON,
validation failure, or an invalid external API response. Consumer threads must not wait or retry in memory, and a
poison message must not block the input queue indefinitely.

## Decision

The input queue dead-letters retryable failures to a retry exchange and queue. The retry queue has a TTL and
dead-letters expired messages back to the input exchange. `x-death` records retry history; when its count reaches
the configured attempt limit, the listener error handler publishes the message to the dead-letter exchange and
acknowledges the failed delivery.

HTTP 5xx, timeout, connection failures, database failures, and an open Circuit Breaker are retryable. Malformed
JSON, validation failures, HTTP 4xx, invalid external responses, and mismatched `userId` are non-retryable and go
directly to the DLQ. The listener acknowledges only after `EnrichmentService` completes successfully.

## Alternatives

- Use `Thread.sleep` or in-memory retry in the consumer.
- Requeue retryable failures immediately on the input queue.
- Route every error to the DLQ without retry.
- Use a client-level HTTP retry in addition to RabbitMQ retry.

## Consequences

Retries are asynchronous and do not occupy consumer threads. The service has at-least-once delivery semantics;
phase 5 idempotency prevents a duplicate `messageId` from producing a second persisted result/outbox event.
Operations can inspect the DLQ after bounded retries. A retry delivery performs one external HTTP call at most.
