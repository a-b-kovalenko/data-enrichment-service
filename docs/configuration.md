# Configuration Reference

Конфігурація задається через `application.yml` та environment variables. Значення
часу використовують ISO-8601 duration, наприклад `PT1S` (одна секунда) або `PT5M`
(п'ять хвилин). Не зберігайте production secrets у файлах репозиторію.

## Spring і локальні залежності

Профіль `local` налаштовує PostgreSQL, RabbitMQ і Liquibase. Його значення можна
перевизначити такими змінними:

- `ENRICHMENT_DATASOURCE_URL` — JDBC URL; за замовчуванням локальний PostgreSQL з
  `currentSchema=enrichment_schema`.
- `ENRICHMENT_DATASOURCE_USERNAME` і `ENRICHMENT_DATASOURCE_PASSWORD` — облікові
  дані PostgreSQL.
- `ENRICHMENT_RABBITMQ_HOST`, `ENRICHMENT_RABBITMQ_PORT`,
  `ENRICHMENT_RABBITMQ_USERNAME`, `ENRICHMENT_RABBITMQ_PASSWORD` — підключення до
  RabbitMQ.

`compose.yaml` надає лише локальні значення: PostgreSQL `user` / `password` і
RabbitMQ `enrichment` / `enrichment`. У production використовуйте secret store або
механізм передачі secrets, прийнятий у вашому середовищі.

## External enrichment API

- `ENRICHMENT_CLIENT_BASE_URL` — base URL external API; default:
  `http://localhost:8081`.
- `ENRICHMENT_CLIENT_CONNECT_TIMEOUT` — час встановлення з'єднання; default:
  `PT1S`.
- `ENRICHMENT_CLIENT_READ_TIMEOUT` — максимальний час очікування відповіді; default:
  `PT2S`.

Збільшення timeout зменшує кількість передчасних retry, але довше займає RabbitMQ
consumer thread. Зменшення timeout швидше звільняє consumer, але може класифікувати
повільну, але справну відповідь як тимчасову помилку.

## Circuit Breaker

- `ENRICHMENT_CLIENT_CIRCUIT_BREAKER_SLIDING_WINDOW_SIZE` — розмір вікна результатів;
  default: `4`.
- `ENRICHMENT_CLIENT_CIRCUIT_BREAKER_MINIMUM_NUMBER_OF_CALLS` — мінімальна кількість
  викликів перед оцінкою failure rate; default: `2`.
- `ENRICHMENT_CLIENT_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD` — відсоток помилок для
  відкриття circuit; default: `50`.
- `ENRICHMENT_CLIENT_CIRCUIT_BREAKER_WAIT_DURATION_IN_OPEN_STATE` — пауза перед
  HALF_OPEN probe; default: `PT5S`.
- `ENRICHMENT_CLIENT_CIRCUIT_BREAKER_PERMITTED_CALLS_IN_HALF_OPEN` — кількість probe
  викликів у HALF_OPEN; default: `1`.

Менше вікно або нижчий threshold швидше захищають залежність, але можуть відкрити
circuit через короткий сплеск помилок. Довший open interval зменшує навантаження на
недоступний API, але довше затримує відновлення normal flow.

## RabbitMQ input, retry і DLQ

- `ENRICHMENT_INPUT_EXCHANGE`, `ENRICHMENT_INPUT_QUEUE`,
  `ENRICHMENT_INPUT_ROUTING_KEY` — адреса вхідних повідомлень; defaults:
  `enrichment.input`, `enrichment.input.queue`, `enrichment.input`.
- `ENRICHMENT_RETRY_EXCHANGE`, `ENRICHMENT_RETRY_QUEUE`,
  `ENRICHMENT_RETRY_ROUTING_KEY` — retry topology; defaults:
  `enrichment.retry`, `enrichment.retry.queue`, `enrichment.retry`.
- `ENRICHMENT_DLX`, `ENRICHMENT_DLQ`, `ENRICHMENT_DLQ_ROUTING_KEY` — dead-letter
  topology; defaults: `enrichment.dlx`, `enrichment.dlq`, `enrichment.dlq`.
- `ENRICHMENT_RETRY_DELAY_MILLISECONDS` — затримка між спробами; default: `5000`.
- `ENRICHMENT_MAX_RETRY_ATTEMPTS` — кількість retry після первинної delivery;
  default: `3`.

Не змінюйте назви queue, exchange, routing key або TTL для вже оголошеної topology
без міграції: RabbitMQ відхиляє повторне оголошення queue з іншими аргументами.
Короткий retry delay швидше відновлює transient failures, але може перевантажити
залежність; довший delay зменшує тиск, але збільшує час обробки повідомлення.

## Transactional outbox publisher

- `ENRICHMENT_OUTBOX_PUBLISHER_ENABLED` — вмикає scheduled publisher; default: `true`.
- `ENRICHMENT_OUTPUT_EXCHANGE` і `ENRICHMENT_OUTPUT_ROUTING_KEY` — адреса output
  events; defaults: `enrichment.output` і `enrichment.output`.
- `ENRICHMENT_OUTBOX_BATCH_SIZE` — максимум ready events за один цикл; default: `20`.
- `ENRICHMENT_OUTBOX_PUBLISH_DELAY` — `fixedDelay` між scheduler runs; default: `PT1S`.
- `ENRICHMENT_OUTBOX_CONFIRM_TIMEOUT` — час очікування broker confirm; default: `PT5S`.
- `ENRICHMENT_OUTBOX_CLAIM_DURATION` — час claim event іншим publisher-ом; default:
  `PT30S`.
- `ENRICHMENT_OUTBOX_INITIAL_BACKOFF` і `ENRICHMENT_OUTBOX_MAX_BACKOFF` — початкова
  та максимальна затримка publication retry; defaults: `PT5S` і `PT5M`.
- `ENRICHMENT_OUTBOX_MAX_ATTEMPTS` — максимальна кількість publication attempts;
  default: `10`.

Більший batch підвищує throughput, але одна scheduler run довше займає broker і БД.
`claim-duration` має бути більшим за нормальний час publication; надто коротке
значення може дозволити повторну обробку event під час повільного confirm. Зміна
outbox backoff впливає лише на output publication, а не на input retry policy.

## Integration profile

`integration-test` вимикає RabbitMQ listener і scheduled outbox publisher за
замовчуванням, встановлює Hibernate `validate` та скорочує retry delay до 100 мс.
Конкретні E2E тести вмикають потрібні асинхронні компоненти через dynamic properties.
Цей профіль призначений лише для automated tests, не для runtime середовища.
