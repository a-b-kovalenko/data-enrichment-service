# ADR 0001: Ports and Adapters Package Structure

## Context

The service receives RabbitMQ messages, calls an external HTTP API, persists data, and later
publishes RabbitMQ events. Infrastructure concerns must not dictate business-flow dependencies.

## Decision

Use a single Gradle module with ports-and-adapters package boundaries. Infrastructure adapters in
`infrastructure` call application services and map transport DTOs to application commands. The
`application` package contains use cases, commands, pure application mappers, and ports. The `domain` package contains
infrastructure-independent models and rules.

## Alternatives

- A layered Spring application where services depend directly on JPA repositories and AMQP clients.
- A Gradle multi-module implementation of the same boundaries.

## Consequences

The project remains simple to build while preserving directional dependencies. Future adapters can
be replaced or moved into Gradle modules without changing domain or application contracts.
