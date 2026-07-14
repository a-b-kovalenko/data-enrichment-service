# 0004. Idempotency and at-least-once delivery

## Context

RabbitMQ can redeliver a message, including after a service failure. Processing the same `messageId` twice must not
create another result or outbox event.

## Decision

The service first reads by `messageId` and returns the existing result without an external HTTP call. PostgreSQL
enforces `result.message_id` as unique and remains the final protection against concurrent deliveries. Result and
outbox event are persisted in one transaction; a duplicate constraint failure creates neither an additional result
nor an outbox event.

## Alternatives

- Rely only on an in-memory duplicate cache.
- Rely only on a read-before-write check.
- Use exactly-once broker delivery.

## Consequences

Delivery remains at least once, while persisted results are idempotent. A concurrent duplicate can still make one
external HTTP call before the database constraint detects it; it cannot commit a second result or outbox event.
