# Data Enrichment Service

Data Enrichment Service — це мікросервіс, який отримує вхідні події через RabbitMQ, збагачує їх за допомогою виклику зовнішнього REST API, атомарно зберігає результати в PostgreSQL та гарантовано публікує вихідні події назад до RabbitMQ.

Усі транспортні контракти сервісу суворо дотримуються стандарту найменування полів `snake_case`.

Оригінальне технічне завдання знаходиться тут: [docs/tz.md](docs/tz.md).

## Архітектура та патерни

Проєкт побудовано на основі **Hexagonal Architecture (Ports and Adapters)** у межах єдиного Gradle-модуля. Залежності суворо спрямовані від периферії до центру:
`infrastructure → application → domain`

1. **Domain**: Містить чисті, технологічно-незалежні бізнес-моделі.
2. **Application**: Описує use cases, команди та порти-інтерфейси.
3. **Infrastructure**: Надає конкретні реалізації адаптерів (JPA для PostgreSQL, RabbitMQ, Spring `RestClient`).

Це дозволяє бізнес-логіці залишатися ізольованою та протестованою незалежно від БД чи брокера повідомлень.

### Транзакційна модель та Transactional Outbox

Процес обробки повідомлення є транзакційно безпечним:
1. HTTP-виклик до зовнішнього API виконується **до** відкриття транзакції бази даних.
2. У межах однієї короткотривалої DB-транзакції відбувається запис збагаченого `result` та супутнього `outbox_event`.
3. Окремий фоновий планувальник (scheduler) публікує `outbox_event` у RabbitMQ. Подія отримує статус `PUBLISHED` лише після отримання підтвердження від брокера (broker confirm).

Цей підхід повністю виключає втрату подій між етапами коміту в БД та публікації повідомлення.

### Ідемпотентність та гарантії доставки

Сервіс використовує семантику **at-least-once delivery**. Ідемпотентність забезпечується унікальним ідентифікатором `message_id`. Повторна доставка раніше обробленого повідомлення не призведе до дублювання `result` чи `outbox_event`. Downstream-консьюмери (наступні сервіси) зобов'язані самостійно дедуплікувати події на основі `message_id`.

### RabbitMQ Топологія та відмовостійкість

Сервіс використовує просунуту топологію RabbitMQ з чітким розділенням помилок на відновлювані (retryable) та невідновлювані (non-retryable):

- **Невідновлювані помилки** (некоректний JSON, помилки валідації, HTTP 4xx, логічні помилки контракту): Одразу маршрутизуються до `enrichment.dlq` (Dead-Letter Queue). 
- **Відновлювані помилки** (проблеми мережі, DB constraints, HTTP 5xx, тайм-аути, відкритий Circuit Breaker): Маршрутизуються до `enrichment.retry.queue`. Після закінчення TTL-затримки подія повертається в основну чергу для повторної обробки. Максимальна кількість спроб обмежена, після чого подія потрапляє до DLQ.

Для захисту зовнішніх API застосовується **Resilience4j Circuit Breaker**. Важливо: HTTP-клієнт не виконує внутрішніх повторів (retry). Натомість всі механізми повтору делегуються RabbitMQ, щоб уникнути блокування потоків виконання.

## Формати даних (Контракти)

**Вхідне повідомлення (`enrichment.input`):**
```json
{
  "message_id": "423e4567-e89b-12d3-a456-426614174000",
  "user_id": 42,
  "action": "request",
  "timestamp": "2026-07-01 10:00:00.0"
}
```

**Відповідь від зовнішнього API (`POST /enrich`):**
```json
{
  "user_id": 42,
  "result": true
}
```

**Вихідне повідомлення (`enrichment.output`):**
```json
{
  "log_id": 1,
  "message_id": "423e4567-e89b-12d3-a456-426614174000",
  "result": true
}
```
*(де `log_id` відповідає первинному ключу таблиці `result`)*

## Технологічний стек

- **Core**: Java 21, Spring Boot 4.x, Gradle
- **Data & Messaging**: PostgreSQL, RabbitMQ, Spring AMQP, Spring Data JPA, Liquibase
- **Mapping**: MapStruct, Lombok
- **Resilience**: Resilience4j Circuit Breaker
- **Testing**: JUnit 5, Mockito, Testcontainers, WireMock, Awaitility, JaCoCo, Checkstyle

## Інструкція для локального запуску

Для розгортання проєкту потрібні Java 21 та Docker Compose.

1. **Ініціалізація конфігурації:**
   ```bash
   cp .env.example .env
   ```
   У файлі `.env` задайте абсолютний шлях для `PROJECT_DATA_DIR` (в цю папку будуть змонтовані дані БД та RabbitMQ).

2. **Запуск інфраструктури (PostgreSQL, RabbitMQ, WireMock):**
   ```bash
   docker compose up -d --wait
   ```

3. **Запуск застосунку:**
   Запустіть `DataEnrichmentServiceApplication` з активним Spring-профілем `local`.

### Налаштування вихідної черги (Smoke Testing)

Сервіс створює вихідний exchange (`enrichment.output`), але не створює чергу консьюмера. Щоб відстежувати результати обробки, вам потрібно задекларувати тестову чергу:

```bash
docker compose exec -T rabbitmq rabbitmqadmin -H localhost -u enrichment -p enrichment \
  declare queue --name enrichment.output.local.queue --durable true --auto-delete false

docker compose exec -T rabbitmq rabbitmqadmin -H localhost -u enrichment -p enrichment \
  declare binding --source enrichment.output --destination-type queue \
  --destination enrichment.output.local.queue --routing-key enrichment.output
```

Панель управління RabbitMQ доступна за адресою `http://localhost:15672` (облікові дані: `enrichment` / `enrichment`).

## Тестування та Quality Gate

Всі pull requests проходять через суворий quality gate. Повне покриття тестами гарантує стабільність бізнес-логіки.

```bash
./gradlew --no-daemon clean test                  # Виконання Unit-тестів
./gradlew --no-daemon integrationTest             # Виконання інтеграційних тестів
./gradlew --no-daemon clean check                 # Виконання повного пайплайну (Checkstyle + тести)
```

**Вимоги покриття коду (JaCoCo):**
- Line coverage: $\ge$ 80%
- Branch coverage: $\ge$ 70%
*(Увага: інтеграційні тести свідомо виключені з розрахунку покриття).*

## Git Workflow

- Зміни до основної гілки `main` вносяться виключно через механізм **Pull Request**. Прямі пуші заблоковані.
- Активуйте локальні pre-push хуки відразу після клонування репозиторію:
  ```bash
  git config --local core.hooksPath .githooks
  ```
