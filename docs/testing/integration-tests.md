# Integration Test Guide

Цей документ пояснює ціль і архітектурну гарантію кожного тесту з
`src/integrationTest`. Тести запускаються окремим Gradle task `integrationTest`.
PostgreSQL піднімається один раз на JVM test suite, а RabbitMQ і WireMock
ізольовані на рівні test class там, де це потрібно.

## EnrichmentFlowIntegrationTest

**Межа:** повний асинхронний flow: RabbitMQ listener, HTTP adapter, PostgreSQL,
transactional outbox і RabbitMQ publisher. Тест використовує реальні PostgreSQL,
RabbitMQ і WireMock. Він пов'язаний з ADR про [outbox](../adr/0002-transactional-outbox.md),
[idempotency](../adr/0004-idempotency-and-delivery-semantics.md),
[retry/DLQ](../adr/0005-rabbitmq-retry-and-dead-letter-queue.md) та
[Circuit Breaker](../adr/0003-external-api-circuit-breaker.md).

### Outbox scheduler

У runtime `OutboxPublisherImpl` викликає `publishPendingEvents()` за розкладом із
`fixedDelay`. Production значення `enrichment.outbox.publish-delay` — `PT1S`:
наступний запуск відбувається через одну секунду після завершення попереднього.

Для цього E2E класу інтервал перевизначено на `PT0.1S` (100 мс). Це скорочує час
очікування output event, не змінюючи production-конфігурацію. Саме scheduler, а не
прямий виклик publisher-а, завершує flow у цьому тесті.

Після класу `@DirtiesContext(AFTER_CLASS)` закриває Spring context разом із
scheduler і RabbitMQ listener. Це не дозволяє фоновій задачі обробити outbox events
наступного test class, який використовує спільний PostgreSQL container.

### processesMessageFromInputToPublishedOutputEvent

Тест надсилає валідний input JSON у `enrichment.input`. WireMock повертає успішну
відповідь external API, після чого тест очікує запис у `result` і повідомлення в
окремій output queue.

Перевіряються тіло HTTP-запиту, поля запису, відповідність `log_id` до `result.id`
та статус outbox event `PUBLISHED`. Це підтверджує основний контракт сервісу від
входу до broker confirm після commit.

### ignoresRepeatedDeliveryOfProcessedMessage

Тест доставляє одне й те саме повідомлення двічі з ідентичним `message_id`.
Після першої доставки він дочікується output event, а після другої перевіряє, що
кількість `result` і outbox event не змінилась.

WireMock повинен отримати лише один HTTP-виклик, а друга output подія не
з'являється. Це підтверджує ідемпотентність на рівні збереженого `message_id`,
а не лише дедуплікацію downstream consumer-ом.

### retriesTransientApiFailureAndCompletesFlowAfterRecovery

WireMock scenario спочатку повертає HTTP 500, а після RabbitMQ delayed retry —
успішну відповідь. Тест очікує `result`, output event і рівно два HTTP-виклики.

HTTP 5xx є тимчасовою помилкою: listener не створює частковий результат і не
відправляє повідомлення у DLQ. Натомість RabbitMQ retry topology повертає його на
input queue, де flow успішно завершується після відновлення API.

### routesNonRetryableApiFailureAndMalformedInputToDeadLetterQueue

Тест публікує два незалежні невиправні повідомлення: валідний JSON, для якого API
повертає 400, та синтаксично некоректний JSON. Обидва повідомлення мають з'явитися
у DLQ.

У PostgreSQL не повинно бути ні `result`, ні outbox event. Це розділяє помилки даних
та контракту від тимчасової недоступності: повторна delivery не здатна виправити
ані HTTP 4xx, ані malformed input.

### retriesDatabaseTransactionFailureWithoutPersistingPartialState

Тест створює тимчасовий PostgreSQL trigger, який відхиляє кожну вставку в `result`.
Після валідного HTTP enrichment listener виконує всі дозволені retry attempts і
повідомлення потрапляє в DLQ.

Перевірка відсутності рядків у `result` і `outbox_event` доводить атомарність
transactional outbox: падіння DB-транзакції не може залишити output event без
результату. Trigger видаляється після тесту.

### retainsOutboxEventUntilOutputRouteIsRestored

Тест видаляє output queue після успішного HTTP enrichment. Outbox publisher не може
отримати route confirm, тому event залишається `PENDING` і фіксується хоча б одна
спроба publication.

Після повторного оголошення queue тест отримує output message та очікує `PUBLISHED`.
Це перевіряє, що тимчасова відсутність RabbitMQ route не призводить до втрати
committed event.

## RestClientEnrichmentWireMockIntegrationTest

**Межа:** `RestClientEnrichmentAdapter` і його Circuit Breaker. WireMock імітує
external API; RabbitMQ і PostgreSQL навмисно не запускаються, бо transport та
persistence семантика покриваються E2E тестом.

### enrichPostsSnakeCaseContractAndMapsSuccessResponse

WireMock очікує `POST /enrich` з `user_id` і `action` у `snake_case` та повертає
валідну success-відповідь. Тест перевіряє, що adapter правильно мапить її в
application model.

Це захищає зовнішній HTTP contract від випадкової зміни JSON naming strategy або
полів request/response.

### enrichClassifiesHttp4xxAndMalformedResponseAsNonRetryable

Спочатку WireMock повертає HTTP 400. Adapter має перетворити його на
`NonRetryableEnrichmentClientException`, тому listener надалі спрямує таку
delivery у DLQ, а не в retry queue.

Потім WireMock повертає HTTP 200 з неповним JSON без `result`. Це також
non-retryable помилка: транспорт успішний, але external API порушив контракт.
Перевірка метрик Circuit Breaker підтверджує, що 4xx і contract errors не є
технічною недоступністю API.

### enrichClassifiesReadTimeoutAsRetryable

WireMock штучно затримує success-відповідь довше за read timeout adapter-а. Виклик
має завершитись `RetryableEnrichmentClientException`.

Timeout означає, що API або мережа тимчасово недоступні. Сам adapter не виконує
HTTP retry; наступну спробу delivery виконує RabbitMQ retry flow.

### enrichOpensCircuitForHttp5xxAndClosesItAfterHalfOpenSuccess

Два HTTP 500 заповнюють невелике тестове вікно Circuit Breaker і переводять його
у `OPEN`. Наступний виклик не надсилається у WireMock, що перевіряється нульовою
кількістю HTTP requests.

Після wait duration WireMock починає повертати success. Awaitility дочікується
HALF_OPEN probe, який успішно закриває Circuit Breaker. Це підтверджує захист API
під час аварії та автоматичне відновлення після неї.

## EnrichmentMessageListenerIntegrationTest

**Межа:** RabbitMQ listener, JSON conversion, Bean Validation і routing помилок.
Application services замінені Mockito beans, щоб цей клас не дублював E2E
перевірку HTTP, persistence та outbox.

### consumesValidJsonMessageThroughApplicationServices

Тест публікує валідний JSON і задає відповідність між `IncomingMessage` та
`EnrichmentCommand`. Awaitility очікує виклик `enrichmentService.enrich(command)`.

Це підтверджує, що listener конвертує transport DTO, застосовує contract service і
передає вже підготовлену application command у правильний use case.

### routesMalformedJsonToDeadLetterQueueWithoutCallingApplicationServices

Тест надсилає JSON, який неможливо десеріалізувати. Повідомлення має потрапити у
DLQ, а обидва application services не повинні бути викликані.

Це гарантує, що невалідний transport payload зупиняється на межі messaging adapter
і не досягає бізнес-логіки.

### routesValidationFailureToDeadLetterQueueWithoutCallingApplicationServices

JSON є синтаксично валідним, але має `user_id` нуль і порожній `action`, тобто
порушує constraints input DTO. Listener відправляє оригінальне повідомлення в DLQ.

Перевірка відсутності взаємодій із services відокремлює validation failure від
бізнесової або transient помилки, які могли б вимагати іншої обробки.

### routesOpenCircuitBreakerToDeadLetterQueueAfterConfiguredAttempts

`enrichmentService` імітує `CallNotPermittedException`, загорнуте в retryable
exception. Listener має повторно доставити повідомлення через retry topology рівно
стільки разів, скільки визначено політикою, а потім відправити його в DLQ.

Open Circuit Breaker не виконує новий HTTP request, але є transient delivery
failure: API може відновитися до наступної RabbitMQ attempt. Обмеження кількості
спроб запобігає нескінченному циклу.

## OutboxPublisherIntegrationTest

**Межа:** `OutboxPublisherImpl`, PostgreSQL claim/update і реальний RabbitMQ broker
confirm. Scheduler не використовується: publisher викликається напряму з
контрольованим `Clock`, щоб перевірити саме publication semantics.

### publishesPendingEventAndMarksItPublishedAfterBrokerConfirm

Тест зберігає ready `PENDING` event, викликає publisher і читає повідомлення з
output queue. Тіло повідомлення має збігатися з outbox payload.

Лише після broker confirm event змінюється на `PUBLISHED` і отримує `published_at`.
Це ключова гарантія transactional outbox: немає позначки про доставку до фактичного
підтвердження RabbitMQ.

### retainsUnroutableEventAndPublishesItAfterRouteRecovery

Тест видаляє output queue, тому mandatory publication стає unroutable. Event лишається
`PENDING`, а `attempt_count` збільшується.

Після відновлення route і переходу контрольованого часу publisher повторює спробу
та успішно публікує event. Це підтверджує backoff/recovery без втрати даних.

## RabbitMqTopologyIntegrationTest

**Межа:** декларація exchanges, queues, bindings, TTL і dead-letter routing без
Spring context. Тест працює з реальним RabbitMQ та закриває власний connection
factory після кожного сценарію.

### routesInputMessageToInputQueue

Тест надсилає payload в input exchange з його routing key та читає його з input
queue. Тіло має збігатися без перетворень.

Це перевіряє базовий binding, від якого залежить увесь listener flow.

### returnsRejectedInputMessageAfterRetryQueueTtl

Тест читає input message вручну та відхиляє його без requeue. DLX направляє його у
retry queue, а після TTL повідомлення повертається в input exchange.

Awaitility підтверджує появу повідомлення в input queue. Це перевіряє delayed retry
без `Thread.sleep` і без блокування RabbitMQ consumer thread.

### routesDeadLetterExchangeMessageToDeadLetterQueue

Тест безпосередньо публікує повідомлення в dead-letter exchange з DLQ routing key.
Воно має з'явитися в `enrichment.dlq` без зміни тіла.

Це ізольовано перевіряє останню ланку topology, на яку покладаються non-retryable
errors і повідомлення, що вичерпали retry attempts.

## LiquibasePersistenceIntegrationTest

**Межа:** Liquibase schema, JPA mappings, persistence adapters і PostgreSQL
constraints. Кожен тест очищає таблиці, тому спільний PostgreSQL container не
переносить domain state між сценаріями.

### createsSchemaWithLiquibaseAndValidatesItWithHibernate

Тест перевіряє наявність таблиць `result` і `outbox_event`, індексу ready outbox
events та двох застосованих Liquibase changesets.

Spring запускається з Hibernate `ddl-auto=validate`, отже тест одночасно підтверджує,
що Liquibase створив схему з нуля і вона відповідає JPA entities.

### persistsAndReadsResultByMessageId

Тест зберігає domain result через adapter, а потім знаходить його через
`message_id`. Перевіряються generated `logId`, correlation identifier і час
створення.

Це підтверджує мапінг між domain model, JPA entity та repository query, яким
application service користується для idempotency check.

### rejectsDuplicateMessageId

Тест двічі зберігає result з тим самим UUID. Другий виклик має завершитися
`DuplicateMessageException`.

Так перевіряється не лише application-level перевірка, а й остаточний захист
унікальним PostgreSQL constraint.

### commitsResultAndOutboxEventInOneTransaction

Тест викликає persistence adapter, який у межах однієї транзакції створює result і
заданий outbox event. Після завершення обидві таблиці повинні містити по одному
рядку.

Це є базовою позитивною перевіркою transactional outbox: результат і намір
опублікувати подію зберігаються разом.

### duplicateDoesNotCreateSecondResultOrOutboxEvent

Перший виклик створює result і автоматично сформований outbox event. Повторний
виклик з тим самим `message_id` кидає `DuplicateMessageException`.

Підсумкові лічильники залишаються рівними одному. Це виключає дубльовані output
events, навіть якщо duplicate delivery пройшла до persistence layer.

### rollsBackResultWhenOutboxEventCannotBePersisted

Тест передає outbox event без обов'язкового `message_id`, через що його збереження
завершується runtime exception. Після помилки обидві таблиці залишаються порожніми.

Це негативна перевірка атомарності: не можна зберегти result, якщо разом із ним не
можна надійно зберегти outbox event.

### findsOnlyPendingOutboxEventsReadyForPublishing

Тест створює ready pending event, pending event із часом у майбутньому та вже
published event. Repository query повертає лише перший запис.

Це підтверджує умови batch selection publisher-а: він не забирає передчасні або вже
оброблені записи.
