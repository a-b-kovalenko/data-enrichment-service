# Data Enrichment Service — agent guide

## Source of truth

- Read `.workspace/tz.md` before implementing a feature.
- Follow the phased delivery plan in `.workspace/implementation-plan.md`.
- Do not begin a later phase until the current phase's tests and completion criteria pass.
- Preserve existing user changes. Do not use destructive Git commands.

## Java coding conventions

### Formatting, naming, and comments

- Use 2 spaces for indentation in Java, XML, YAML, JSON, and web files.
- Keep lines at or below 120 characters.
- Use `PascalCase` for classes and interfaces, `camelCase` for methods and variables,
  `UPPER_SNAKE_CASE` for `static final` constants, and lowercase dot-separated package names.
- Write technical comments and JavaDoc in English.

### Imports and clean code

- Use normal imports. Do not use fully qualified class names in signatures, implementation, or tests.
- Use explicit imports only; wildcard imports are forbidden.
- Use static imports for assertions, matchers, and utility methods, for example AssertJ and Mockito methods.
- Prefer `var` for local variables when the inferred type remains obvious to a reader.
- Extract repeated literals, section labels, and magic strings into
  `private static final` or `public static final` constants.

### Spring, Lombok, and Java 21

- Use constructor injection for `final` dependencies. Field injection and field-level `@Autowired` are forbidden.
- Prefer Lombok `@RequiredArgsConstructor` and
  `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)` for Spring components with dependencies.
- Define application services as interfaces. Place their Spring implementations in the corresponding `impl` package.
- Use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor` for entities and DTOs when applicable.
- Use Java 21+ features where they improve clarity: records, pattern matching, text blocks, and `var`.

### Logging write operations

- A method that writes to a database, publishes a broker message, writes a file, or invokes another write-side
  operation must log its entry before the side effect.
- Log only safe correlation identifiers and operation context, for example `messageId`, `logId`, or an entity ID.
  Never log complete transport payloads or sensitive data.

### JSON contracts

- All JSON request and response property names must use `snake_case`.
- Configure Jackson globally for `snake_case`; do not rely on scattered per-field annotations
  unless an external contract explicitly requires an exception.
- RabbitMQ payloads, WireMock stubs, and JSON assertions must use the same `snake_case` property names.

### Complexity and tests

- Keep cyclomatic complexity low. Extract loops, branching, and nested blocks into descriptively named private methods.
- Keep nesting depth at no more than two levels.
- Prefer AssertJ fluent assertions and `.extracting(...)` for multi-field checks. Prefer field names in
  `.extracting("fieldName", ...)` when they are available.
- Use `@SneakyThrows` in tests or test helpers when it avoids boilerplate for checked exceptions.

## Delivery workflow

1. Implement one coherent item from the active phase.
2. Add or update the tests specified for that item.
3. After every completed logical Java, Gradle, configuration, or test change, run:

   ```bash
   ./gradlew --no-daemon clean build
   ```

   Resolve every failure before continuing with the next logical change.
4. During diagnosis, run the smallest relevant Gradle task first.
5. Before declaring a phase complete, run:

   ```bash
   ./gradlew clean test
   ./gradlew integrationTest
   ./gradlew jacocoTestReport jacocoTestCoverageVerification
   ./gradlew check
   ```

6. Report changed files, executed checks, and any unresolved constraint.

`check` must depend on unit tests, integration tests, and JaCoCo verification.

## GitHub and CI

- The protected primary branch is `main`. Do not push to it directly; create a feature branch
  and merge only through a pull request.
- Do not run `git add`, `git commit`, or `git push` without explicit user authorization.
  Authorization to commit does not authorize a push; authorization to push does not authorize a commit.
- GitHub Actions CI is defined in `.github/workflows/ci.yml` and must run
  `./gradlew --no-daemon clean check` on pull requests to `main`.
- Keep CI permissions read-only unless a reviewed requirement needs more access.
- A pull request may be merged only after the required CI check succeeds, review approval is
  present, and all review conversations are resolved.
- Do not weaken or bypass the GitHub branch ruleset. Its required setup is documented in
  `docs/github-setup.md`.
- Configure `core.hooksPath` to `.githooks`. The versioned `pre-push` hook blocks direct updates
  and deletion of `main` and `master`; do not bypass it with `--no-verify`.

## Project layout

```text
src/main/java                 Application source
src/main/resources            Runtime configuration and Liquibase
src/test/java                 Unit tests only
src/test/resources            Unit-test resources
src/integrationTest/java      Integration tests only
src/integrationTest/resources Integration-test resources
docs/adr                      Architecture Decision Records
```

- Put a test in `src/integrationTest` if it starts Spring context, Testcontainers, RabbitMQ,
  PostgreSQL, or a WireMock server.
- Keep business-logic tests in `src/test` and use Mockito there.
- Do not place integration tests in `src/test`.

## Build, testing, and coverage

- Use Java 21 and Spring Boot 4.x.
- Configure a dedicated Gradle `integrationTest` source set and `Test` task.
- Use Checkstyle with `config/checkstyle/checkstyle.xml` as the Java lint configuration.
- `check` must run Checkstyle for `main`, `test`, and `integrationTest` source sets.
- Do not suppress Checkstyle violations without a documented, narrowly scoped reason.
- Use JUnit 5, Mockito, AssertJ, Testcontainers, WireMock, and Awaitility as appropriate.
- JaCoCo must collect execution data only from `test`; disable it for `integrationTest`.
- Integration-test execution data must not appear in JaCoCo reports or verification.
- Enforce at least 80% line and 70% branch coverage for eligible production code.
- Exclude only generated code, application bootstrap, pure configuration, simple transport DTOs, JPA entities,
  and MapStruct mapper implementations. Do not exclude business services, listeners, or outbox publishers.
- Use Awaitility for asynchronous assertions. Do not use `Thread.sleep` in tests.

## Architecture and transaction rules

- Keep dependencies directional: messaging and HTTP adapters call application ports; application code
  must not depend on AMQP, JPA, or HTTP implementation classes.
- The enrichment HTTP request must finish before the short database transaction starts.
  Never hold a DB transaction open during the HTTP call.
- In one database transaction, persist both `result` and its `outbox_event`.
- Publish output messages only through the outbox publisher after database commit.
- Delivery is at-least-once. Use `messageId` and the unique `result.message_id` constraint for idempotency.
- A duplicate input must not create another `result` or outbox event.
- Outbox publishing requires broker confirms; mark an event as published only after confirmation.
- Concurrent publishers must safely claim pending events, e.g. with `FOR UPDATE SKIP LOCKED`.

## RabbitMQ error handling

- Define input exchange, queue, routing key, retry queues, DLX, DLQ, output exchange,
  and output routing key in configuration.
- Acknowledge input only after application processing has completed successfully.
- Invalid JSON, validation failures, HTTP 4xx, and invalid external responses are non-retryable and go to the DLQ.
- Timeouts, connection errors, HTTP 5xx, database availability failures, and an open circuit are retryable.
- Perform delayed retry with RabbitMQ TTL/DLX retry queues and bounded attempts.
  Do not block consumer threads with sleeping or in-memory retry loops.
- Propagate `messageId` into structured logs/correlation data. Avoid logging full sensitive payloads.

## External API and Circuit Breaker

- Use OpenAPI only when this service exposes its own HTTP endpoints. The external `POST /enrich` client uses
  application DTOs and WireMock contract tests.
- Use Spring `RestClient` with configured connection and read timeouts.
- Verify that `userId` in the external response matches the request.
- Wrap the real HTTP call in a Resilience4j Circuit Breaker.
- Circuit Breaker records timeouts, connection errors, and HTTP 5xx as failures.
  It must ignore HTTP 4xx, validation errors, and contract errors.
- Treat `CallNotPermittedException` from an OPEN circuit as retryable. No HTTP request may be issued in this state.
- Do not add an HTTP-level Resilience4j Retry around this client. RabbitMQ owns delivery retry,
  so one delivery attempts at most one actual HTTP call.
- Configure and test CLOSED, OPEN, and HALF_OPEN behavior.

## Persistence and Liquibase

- PostgreSQL schema changes must be Liquibase XML changelogs under `src/main/resources/db/changelog`.
- The master XML changelog contains includes only.
- Each migration uses SQL inside `<sql>`. Do not add Liquibase rollback blocks.
  Do not use Liquibase schema DSL tags such as `createTable`, `addColumn`, or `createIndex`.
- Use `CDATA` only when SQL contains XML-reserved characters such as `<`, `>`, or `&`.
- Do not use Hibernate `ddl-auto` to create schema. Test profiles validate the Liquibase-created schema.
- `result.message_id` is `UUID NOT NULL UNIQUE`; persist event time and technical creation time separately.

## Docker Compose

- `compose.yaml` belongs to phase 1 and starts PostgreSQL and RabbitMQ for local development.
- Read `PROJECT_DATA_DIR` from a local `.env` file. Commit `.env.example`, but never commit `.env`.
- Persist data under `${PROJECT_DATA_DIR}` with valid bind mounts, for example:

  ```yaml
  services:
    postgres:
      volumes:
        - ${PROJECT_DATA_DIR}/postgres:/var/lib/postgresql/data
    rabbitmq:
      volumes:
        - ${PROJECT_DATA_DIR}/rabbitmq:/var/lib/rabbitmq
  ```

- Validate Compose with `docker compose config` and verify container health checks when Compose changes.

## Documentation

- Maintain ADRs in `docs/adr` using Context, Decision, Alternatives, and Consequences.
- Required ADRs cover architecture style, transactional outbox, idempotency/delivery semantics,
  retry/DLQ strategy, and external API Circuit Breaker.
- Keep README aligned with actual configuration, topology, test commands, contracts, and delivery guarantees.
