# ADR 0002: Transactional Outbox

## Context

Persisting an enrichment result and publishing a RabbitMQ event are separate resource operations.
If the database transaction succeeds but the broker publish fails, the service can lose an output
event. Publishing before the database commit can instead expose an event for data that is later
rolled back.

## Decision

Store `result` and a `PENDING` `outbox_event` in the same short database transaction. The external
enrichment HTTP call remains outside that transaction. A separate publisher will read committed
outbox records and publish them to RabbitMQ, marking an event as published only after broker
confirmation. The `message_id` unique constraint provides the final idempotency guard for the
incoming delivery.

## Alternatives

- Publish directly to RabbitMQ from the application transaction.
- Publish to RabbitMQ before saving the result.
- Use a distributed XA transaction between PostgreSQL and RabbitMQ.

## Consequences

The database provides an atomic durable handoff from the application flow to the future publisher.
The publisher, retry state and operational monitoring add implementation complexity. Delivery is
at least once, so consumers of the output event must tolerate duplicates.
