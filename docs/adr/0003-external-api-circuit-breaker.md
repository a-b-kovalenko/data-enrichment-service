# 0003. Circuit Breaker for the external enrichment API

## Context

An unavailable enrichment API must not make RabbitMQ consumer threads wait for repeated HTTP timeouts. Delivery retry
and delayed redelivery belong to RabbitMQ and must not be duplicated in the HTTP client.

## Decision

Wrap one `RestClient` call in a Resilience4j Circuit Breaker. HTTP 5xx, connection failures and timeouts are
retryable and recorded as failures. HTTP 4xx, incomplete responses and mismatched `userId` are non-retryable and do
not affect the failure rate. An open circuit produces a retryable exception without issuing HTTP. Circuit parameters
are configured in `application.yml`; no Resilience4j HTTP-level Retry is configured.

## Alternatives

- Retry HTTP calls within the client.
- Let every consumer wait for the HTTP timeout.
- Classify all HTTP and contract failures as Circuit Breaker failures.

## Consequences

Each input delivery performs at most one HTTP attempt. During an outage the open circuit fails quickly and can be
routed to RabbitMQ's delayed retry flow in phase 6. A recovered service is probed through HALF_OPEN before normal
traffic is restored.
