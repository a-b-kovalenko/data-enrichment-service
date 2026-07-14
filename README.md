# Data Enrichment Service

Сервіс отримує повідомлення з RabbitMQ.
Він збагачує їх через зовнішній REST API.
Після цього зберігає результат у PostgreSQL.
Підсумкову подію сервіс надсилає в RabbitMQ.

Проєкт реалізується за тестовим завданням.
Наразі завершено правила розробки.
Також є базова Gradle-інфраструктура.
Бізнес-функціональність перебуває у фазі реалізації.

## Запланований flow

```text
RabbitMQ input
    → validation
    → external enrichment API
    → PostgreSQL: result + outbox_event (one transaction)
    → RabbitMQ output
```

Вхідне повідомлення містить `message_id`, `user_id`, `action` і `timestamp`.
Зовнішній API повертає `user_id` та `result`. Вихідна подія містить `log_id`,
`message_id` і `result`.

Усі JSON request/response властивості використовують `snake_case`.

## Архітектурні принципи

- Spring Boot 4.x і Java 21.
- RabbitMQ для input, retry, DLQ та output topology.
- PostgreSQL і Liquibase XML changelog-и з PostgreSQL SQL у `<sql>` блоках.
- Transactional outbox: `result` і `outbox_event` створюються в одній транзакції.
- Idempotency через унікальний `message_id`.
- At-least-once delivery для output events; downstream consumers мають дедуплікувати події.
- OpenAPI-first контракт для зовнішнього `POST /enrich` API.
- Resilience4j Circuit Breaker для зовнішнього API; retry delivery виконує RabbitMQ,
  а не HTTP-клієнт.

## Технології

- Java 21, Gradle, Spring Boot 4.x;
- Spring AMQP, Spring Data JPA, Spring Web `RestClient`;
- PostgreSQL, RabbitMQ, Liquibase;
- Lombok, MapStruct, OpenAPI Generator;
- JUnit 5, Mockito, Testcontainers, WireMock, Awaitility;
- JaCoCo, Checkstyle, GitHub Actions.

## Якість коду і тести

```bash
./gradlew --no-daemon check
```

Команда `check` є обов'язковим quality gate та в CI запускатиме:

- Checkstyle для Java-коду;
- unit-тести з `src/test`;
- integration-тести з `src/integrationTest`;
- JaCoCo coverage verification лише за unit-тестами.

Цільові пороги JaCoCo: не менше 80% line coverage і 70% branch coverage
для eligible production code.
Integration-тести не впливають на JaCoCo coverage.

На поточному етапі в репозиторії ще немає application source code.
Тому Gradle може показувати `NO-SOURCE`.
Повна test infrastructure буде додана у фазі 1.

## Стиль коду

- 2 пробіли для Java, XML, YAML і JSON;
- максимум 120 символів у рядку;
- explicit imports без wildcard imports і FQCN у Java-коді;
- constructor injection; field injection заборонений;
- JSON поля — тільки `snake_case`;
- технічні коментарі та JavaDoc — англійською.

Повні правила: [.agents/AGENTS.md](.agents/AGENTS.md).

## Git workflow і CI

- Основна гілка — `main`; прямий push заборонений.
- Зміни потрапляють у `main` лише через pull request.
- CI запускає `./gradlew --no-daemon clean check` для pull request у `main`.
- Versioned pre-push hook блокує оновлення та видалення `main` і `master`.

Після клонування активуйте hook:

```bash
git config --local core.hooksPath .githooks
```

Інструкція з GitHub branch ruleset: [docs/github-setup.md](docs/github-setup.md).

## Документація

ADR-документи будуть розміщені у `docs/adr/` і зафіксують рішення щодо:

- архітектури;
- transactional outbox;
- idempotency і delivery semantics;
- OpenAPI external client;
- retry/DLQ strategy;
- Circuit Breaker.

## Стан проєкту

Перед реалізацією бізнес-flow потрібно завершити фазу 1.
Вона додає Spring Boot залежності, `integrationTest`, JaCoCo,
Docker Compose, Liquibase та OpenAPI infrastructure.
