# Operations Runbook

Цей runbook призначений для локального середовища з `compose.yaml`. Для production
використовуйте ті самі принципи через затверджені інструменти доступу до RabbitMQ і
PostgreSQL; не змінюйте черги або записи напряму без погодженої процедури.

## Швидка перевірка стану

Переконайтеся, що локальні залежності запущені:

```bash
docker compose ps
```

RabbitMQ Management UI доступний на `http://localhost:15672`. Там можна переглянути
кількість ready та unacknowledged messages у `enrichment.input.queue`,
`enrichment.retry.queue` і `enrichment.dlq`.

Для PostgreSQL можна відкрити shell:

```bash
docker compose exec postgres psql -U user -d enrichment
```

Локальний Spring profile використовує схему `enrichment_schema`.

## Діагностика input і retry queues

- Повідомлення в `enrichment.input.queue` чекає на listener або ще не було оброблене.
- Повідомлення в `enrichment.retry.queue` очікує TTL перед наступною спробою. Це
  нормальний стан під час тимчасової помилки.
- Постійне зростання retry queue означає, що залежність не відновлюється або delay
  занадто малий для поточного навантаження.
- `enrichment.dlq` містить повідомлення, які не можна обробити автоматично або які
  вичерпали ліміт retry attempts.

Спочатку перевірте application logs за `messageId`. Для retryable помилки потрібно
усунути причину: відновити external API, RabbitMQ або PostgreSQL. Для non-retryable
помилки виправте input payload або контракт external API.

## Робота з DLQ

DLQ — це не черга для автоматичного повтору. Повідомлення з неї потрібно спочатку
проаналізувати, і лише після виправлення причини повторно подати в input flow.

1. У RabbitMQ UI відкрийте `enrichment.dlq` і перегляньте payload та headers.
2. Знайдіть `message_id` і перевірте, чи вже є результат у БД:

   ```sql
   SELECT id, message_id, created_at
   FROM enrichment_schema.result
   WHERE message_id = '<message-id>';
   ```

3. Якщо запис уже існує, повторна delivery того самого `message_id` не створить
   дубль завдяки idempotency. З'ясуйте, чому повідомлення опинилося в DLQ, перш ніж
   повторювати його.
4. Якщо запису немає, виправте причину: наприклад, JSON, `user_id`, `action` або
   external API. Потім опублікуйте виправлене повідомлення в `enrichment.input` з
   routing key `enrichment.input`. Для ручного локального запуску зручно використати
   Bruno collection `Publish input message`.

Не змінюйте `message_id`, якщо перевіряєте повторну delivery того самого бізнесового
повідомлення. Змініть його лише для нової незалежної команди.

## Стан transactional outbox

Outbox event проходить стани `PENDING`, `PUBLISHED` і `FAILED`. `PENDING` означає,
що подія ще очікує publication або наступної спроби; `PUBLISHED` — RabbitMQ
підтвердив приймання і маршрутизацію повідомлення; `FAILED` — вичерпано ліміт
outbox attempts і потрібне ручне втручання.

Поточний стан можна подивитися так:

```sql
SELECT id, message_id, status, attempt_count, next_attempt_at, published_at
FROM enrichment_schema.outbox_event
ORDER BY created_at DESC;
```

Для проблемних event корисний запит:

```sql
SELECT id, message_id, status, attempt_count, next_attempt_at, payload
FROM enrichment_schema.outbox_event
WHERE status IN ('PENDING', 'FAILED')
ORDER BY next_attempt_at, id;
```

Якщо event довго лишається `PENDING`, перевірте output exchange і binding queue
отримувача. Якщо event став `FAILED`, не змінюйте статус у БД навмання: спочатку
відновіть route або broker, перевірте payload і визначте погоджену процедуру
повторної публікації.

## Перевірка output route

Сервіс публікує в `enrichment.output`, але не створює queue для отримувачів. Кожен
downstream consumer має оголосити власну durable queue і binding з routing key
`enrichment.output`.

Для локального smoke flow створіть у RabbitMQ Management UI queue
`enrichment.output.local.queue` як durable і додайте binding:

- source exchange: `enrichment.output`;
- destination type: `queue`;
- destination: `enrichment.output.local.queue`;
- routing key: `enrichment.output`.

Те саме через термінал:

```bash
docker compose exec -T rabbitmq rabbitmqadmin -H localhost -u enrichment -p enrichment \
  declare queue --name enrichment.output.local.queue --durable true --auto-delete false
docker compose exec -T rabbitmq rabbitmqadmin -H localhost -u enrichment -p enrichment \
  declare binding --source enrichment.output --destination-type queue \
  --destination enrichment.output.local.queue --routing-key enrichment.output
```

Якщо такої queue немає, mandatory publish повертається publisher-у як unroutable.
Outbox event лишається `PENDING` і буде повторений з backoff. Це захищає від втрати
події, але не замінює коректне налаштування consumer-а.

## Безпечні межі ручних дій

- Не purge input, retry або DLQ queue, поки не збережено payload для аналізу.
- Не видаляйте `result` або `outbox_event` для обходу idempotency.
- Не вручну позначайте event як `PUBLISHED`: це має робити лише publisher після
  RabbitMQ confirm.
- Перед зміною RabbitMQ topology зупиніть consumer-ів або виконайте погоджену
  міграцію; RabbitMQ не дозволяє повторно оголосити queue з іншими аргументами.
