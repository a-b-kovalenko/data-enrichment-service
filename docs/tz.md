# Тестове завдання: Data Enrichment Service

## Мета

Розробити Spring Boot застосунок, який асинхронно обробляє повідомлення з черги,
збагачує їх через зовнішній REST API, зберігає результат у PostgreSQL і сповіщає
інші системи про успішне виконання.

## Технологічний стек

- Java 21 або новіша;
- Spring Boot 4.x: Spring Web, Spring Data JPA, Spring AMQP;
- PostgreSQL;
- RabbitMQ;
- JUnit 5, Mockito і Testcontainers для тестування.

## Бізнес-процес

### 1. Отримання повідомлення

Сервіс слухає RabbitMQ queue та отримує JSON-команду з ідентифікатором повідомлення,
ідентифікатором користувача, дією і часом події.

```json
{
  "message_id": "123e4567-e89b-12d3-a456-426614174000",
  "user_id": 12345678,
  "action": "request",
  "timestamp": "2026-07-01 10:00:00.0"
}
```

### 2. Збагачення через external API

Після отримання команди сервіс виконує `POST` до зовнішнього сервісу. Для
тестування цей сервіс може імітуватися WireMock або іншою заглушкою.

Тіло запиту містить `user_id` і `action`:

```json
{
  "user_id": 12345678,
  "action": "request"
}
```

Очікувана відповідь:

```json
{
  "user_id": 12345678,
  "result": true
}
```

### 3. Збереження в PostgreSQL

Після успішного enrichment сервіс об'єднує оригінальні та збагачені дані й зберігає
їх у таблиці `result`.

Мінімальні дані запису:

- `id` — первинний ключ;
- `message_id` — UUID із input message, унікальний;
- `user_id`;
- `action`;
- `result`;
- `created_at`.

### 4. Відправка результату

Після commit у БД сервіс публікує подію у RabbitMQ exchange.

```json
{
  "log_id": 654321,
  "message_id": "123e4567-e89b-12d3-a456-426614174000",
  "result": true
}
```

## Тестування

- Написати unit-тести бізнес-логіки за допомогою Mockito.
- Написати integration test із Testcontainers для PostgreSQL і RabbitMQ, який
  перевіряє шлях від input message до запису в БД та output message.

## Очікуваний результат

Надати код проєкту та README з коротким описом архітектурних рішень, зокрема
обробки помилок і транзакцій.

## Реалізація в цьому репозиторії

Фактичні JSON контракти, RabbitMQ topology, retry/DLQ policy, transactional outbox
та локальний запуск описані в [README](../README.md). Архітектурні рішення
зафіксовані в [ADR](adr/README.md).
