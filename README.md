# Data Enrichment Service

Data Enrichment Service отримує команду з RabbitMQ, викликає зовнішній REST API,
зберігає збагачений результат у PostgreSQL і публікує підсумкову подію в RabbitMQ.
Усі transport-контракти використовують `snake_case`.

Початкове технічне завдання: [docs/tz.md](docs/tz.md).

## Flow і транзакційна модель

```text
RabbitMQ input
    → validation
    → external enrichment API
    → PostgreSQL: result + outbox_event (одна транзакція)
    → RabbitMQ output
```

HTTP-виклик завершується до відкриття короткої DB-транзакції. У транзакції разом
створюються `result` і `outbox_event`; тільки committed outbox event може бути
опублікований scheduler-ом. `PUBLISHED` встановлюється тільки після RabbitMQ broker
confirm. Це усуває втрату події між DB commit і broker publish.

Повторна доставка того самого `message_id` не створює другого `result` або outbox
event. Delivery має семантику at-least-once, тому downstream consumer повинен
дедуплікувати output event за `message_id`.

### Контракти

Input (`enrichment.input`):

```json
{
  "message_id": "423e4567-e89b-12d3-a456-426614174000",
  "user_id": 42,
  "action": "request",
  "timestamp": "2026-07-01 10:00:00.0"
}
```

External API: `POST /enrich` із `{"user_id":42,"action":"request"}`; відповідь:
`{"user_id":42,"result":true}`. `user_id` у відповіді має збігатися з запитом.

Output (`enrichment.output`):

```json
{
  "log_id": 1,
  "message_id": "423e4567-e89b-12d3-a456-426614174000",
  "result": true
}
```

`log_id` дорівнює `result.id`.

## Архітектура

- Spring Boot 4.x і Java 21.
- RabbitMQ для input, retry, DLQ та output topology.
- PostgreSQL і Liquibase XML changelog-и з PostgreSQL SQL у `<sql>` блоках.
- Transactional outbox: `result` і `outbox_event` створюються в одній транзакції.
- Idempotency через унікальний `message_id`.
- At-least-once delivery для output events; downstream consumers мають дедуплікувати події.
- OpenAPI використовуватимемо лише для власних HTTP endpoint-ів, якщо вони з'являться.
- Resilience4j Circuit Breaker для зовнішнього API; retry delivery виконує RabbitMQ,
  а не HTTP-клієнт.

Застосунок використовує Ports and Adapters (Hexagonal) архітектуру в межах одного
Gradle-модуля:

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

### RabbitMQ topology та помилки

```text
                           ┌───────────────────────────┐
                           │ enrichment.input exchange │
                           └─────────────┬─────────────┘
                                         │ enrichment.input
                                         ▼
                           enrichment.input.queue → listener
                                  │                    │ success
                   retryable      │                    ▼
                                  ▼          PostgreSQL: result + outbox_event
                    enrichment.retry exchange                    │
                                  │ enrichment.retry             ▼
                                  ▼                 outbox publisher + broker confirm
                    enrichment.retry.queue (TTL)                 │
                                  │ DLX after delay              ▼
                                  └───────────────► enrichment.output exchange
                                                       (downstream bindings)

non-retryable or retry limit → enrichment.dlx → enrichment.dlq
```

Input, retry і dead-letter exchanges працюють як точні маршрутизатори: exchange
порівнює routing key повідомлення з ключем queue і передає його за потрібною
“адресою”. Нові повідомлення з ключем `enrichment.input` потрапляють до
`enrichment.input.queue`, де їх обробляє listener.

Якщо помилка тимчасова, RabbitMQ переносить повідомлення у
`enrichment.retry.queue`. Там воно чекає `ENRICHMENT_RETRY_DELAY_MILLISECONDS`
(за замовчуванням 5 секунд), а потім повертається в input queue для нової спроби.
Після `ENRICHMENT_MAX_RETRY_ATTEMPTS` (за замовчуванням 3) повідомлення потрапляє
в `enrichment.dlq`; разом це первинна спроба і до трьох повторів.

Некоректний JSON, validation error, HTTP 4xx або неправильна відповідь API одразу
йдуть у DLQ: повторення не може виправити дані. HTTP 5xx, timeout, проблеми мережі
або БД та відкритий Circuit Breaker вважаються тимчасовими, тому сервіс повторить
обробку пізніше. HTTP-клієнт сам не робить повторів — одну delivery обробляють один
раз, а всі повтори виконує RabbitMQ.

Після DB commit scheduler вибирає ready outbox events і публікує їх у
`enrichment.output` з routing key `enrichment.output`. Це exchange для розсилки, а
не одна спільна черга: кожен сервіс-отримувач створює власну durable queue і
підключає її до `enrichment.output`. Так кожен отримувач отримує свою копію події.

Event стає `PUBLISHED` лише коли RabbitMQ підтвердив, що прийняв повідомлення і
знайшов для нього такий маршрут. Якщо немає підключеної queue або broker тимчасово
недоступний, event лишається `PENDING`, а publisher повторить його з окремим backoff.

Докладні рішення: [архітектура](docs/adr/0001-architecture-style.md),
[outbox](docs/adr/0002-transactional-outbox.md),
[Circuit Breaker](docs/adr/0003-external-api-circuit-breaker.md),
[idempotency](docs/adr/0004-idempotency-and-delivery-semantics.md),
[retry/DLQ](docs/adr/0005-rabbitmq-retry-and-dead-letter-queue.md).

## Технології

- Java 21, Gradle, Spring Boot 4.x;
- Spring AMQP, Spring Data JPA, Spring Web `RestClient`;
- PostgreSQL, RabbitMQ, Liquibase;
- Lombok, MapStruct;
- JUnit 5, Mockito, Testcontainers, WireMock, Awaitility;
- JaCoCo, Checkstyle, GitHub Actions.

## Запуск локально

Потрібні Java 21, Docker Compose і Bruno (лише для ручного smoke test).

```bash
cp .env.example .env
docker compose up -d --wait
```

У `.env` задайте абсолютний `PROJECT_DATA_DIR`; файл містить лише локальний шлях і
не комітиться. Compose піднімає PostgreSQL 16 (`localhost:5432`), RabbitMQ
(`localhost:5672`, UI `http://localhost:15672`) та WireMock (`http://localhost:8081`).
Облікові дані локального compose: PostgreSQL `user` / `password`, RabbitMQ
`enrichment` / `enrichment`.

Запустіть `DataEnrichmentServiceApplication` з профілем `local`. Конфігурація
підтримує environment variables: `ENRICHMENT_DATASOURCE_*`, `ENRICHMENT_RABBITMQ_*`,
`ENRICHMENT_CLIENT_BASE_URL`, `ENRICHMENT_INPUT_*`, `ENRICHMENT_OUTBOX_*` та інші
властивості з [application.yml](src/main/resources/application.yml). Секрети в
репозиторії відсутні; показані значення призначені тільки для локального Compose.

Для ручного smoke flow відкрийте `bruno`, виберіть environment `local` і виконайте
`Publish input message`. Перед повтором змініть `message_id`.

## Тести і quality gate

```bash
./gradlew --no-daemon clean test
./gradlew --no-daemon integrationTest
./gradlew --no-daemon jacocoTestReport jacocoTestCoverageVerification
./gradlew --no-daemon clean check
```

Команда `check` є обов'язковим quality gate та в CI запускатиме:

- Checkstyle для Java-коду;
- unit-тести з `src/test`;
- integration-тести з `src/integrationTest`;
- JaCoCo coverage verification лише за unit-тестами.

Цільові пороги JaCoCo: не менше 80% line coverage і 70% branch coverage
для eligible production code.
Integration-тести не впливають на JaCoCo coverage.

Integration-тести піднімають PostgreSQL і RabbitMQ у Testcontainers та WireMock для
external API. `EnrichmentFlowIntegrationTest` перевіряє повний шлях input → HTTP →
`result` + outbox → output, повторну delivery, retry після API 5xx, DLQ для 4xx та
malformed input, а також recovery нерозісланого outbox event. Integration tests не
входять у JaCoCo execution data.

Докладне пояснення кожного integration scenario: [Integration Test Guide](docs/testing/integration-tests.md).
Практична діагностика: [Operations Runbook](docs/operations.md).
Довідник environment variables і tuning: [Configuration Reference](docs/configuration.md).

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

## Стан проєкту

Завершено фази 1–9: фундамент, контракти, persistence, HTTP client із Circuit
Breaker, idempotent application flow, RabbitMQ listener із retry/DLQ,
transactional outbox publisher, повний E2E integration flow і фінальна документація.
