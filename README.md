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

## Архітектура застосунку

Застосунок використовує практичний варіант Ports and Adapters (Hexagonal)
архітектури в межах одного Gradle-модуля:

```text
RabbitMQ / HTTP / PostgreSQL
            │
            ▼
    infrastructure adapters
            │
            ▼
 application services + ports
            │
            ▼
       domain models
```

- `domain` містить незалежні від технологій бізнес-моделі та правила.
- `application` містить use cases, команди, application mapper-и й порти-інтерфейси.
- `infrastructure` містить конкретні адаптери для JPA/PostgreSQL, RabbitMQ, HTTP і Spring.

Залежності спрямовані всередину: `infrastructure → application → domain`.
Наприклад, application service залежить від `ResultPersistencePort`, а
`ResultPersistenceAdapter` реалізує цей порт через JPA та PostgreSQL. Завдяки цьому
business-flow не залежить від JPA чи RabbitMQ і тестується через mock портів.

Докладне обґрунтування: [ADR 0001](docs/adr/0001-architecture-style.md).

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

Поточна база містить Spring Boot application class, unit smoke test та окремий
`integrationTest` source set. Бізнес-flow буде додано в наступних фазах.

## Стиль коду

- 2 пробіли для Java, XML, YAML і JSON;
- максимум 120 символів у рядку;
- explicit imports без wildcard imports і FQCN у Java-коді;
- constructor injection; field injection заборонений;
- JSON поля — тільки `snake_case`;
- технічні коментарі та JavaDoc — англійською.

Повні правила: [.agents/AGENTS.md](.agents/AGENTS.md).

## Локальні Docker-дані

`compose.yaml` запускає PostgreSQL 16 і RabbitMQ для локальної розробки. Для
portable bind mounts він читає `PROJECT_DATA_DIR` із локального `.env`.

Створіть його на основі versioned прикладу:

```bash
cp .env.example .env
```

Вкажіть у `.env` абсолютний шлях до каталогу даних на вашій машині.
Файл `.env` ігнорується Git.

Запуск і перевірка health checks:

```bash
docker compose up -d --wait
docker compose ps
```

PostgreSQL доступний на `localhost:5432`, RabbitMQ — на `localhost:5672`, а
RabbitMQ Management UI — на `http://localhost:15672`.

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

Фаза 1 завершена: налаштовано Spring Boot, `integrationTest`, JaCoCo,
Docker Compose, Liquibase і OpenAPI infrastructure. Наступна фаза додає
контракти, доменні моделі та MapStruct mapper-и.
