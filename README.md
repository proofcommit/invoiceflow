# InvoiceFlow

InvoiceFlow is an invoice and payment workflow with two Spring Boot services: a REST API and a notification consumer. The baseline is Spring Boot 2.7.18 on Java 17, using `javax.*` APIs and infrastructure versions appropriate to that generation.

It creates draft invoices, issues them, records partial or full external payments, prevents overpayment, and publishes invoice events through a transactional outbox. The notification service consumes those events and delivers invoice and payment emails through SMTP. It is an invoicing product, not a payment gateway: it records confirmed external payments and does not charge cards or move money.

## Product behavior

- Create invoices with customer, currency, due date, and line items
- Calculate totals and outstanding balances with decimal money arithmetic
- Issue draft invoices through an explicit state transition
- Record partial and final payments
- Treat repeated payment references idempotently
- Reject overpayment and payments against drafts or paid invoices
- Serialize concurrent payment updates with a database row lock
- Publish `invoice.issued` and `invoice.payment.recorded` events
- Persist event intent in the same database transaction as business state
- Confirm RabbitMQ publication before marking an outbox event published
- Consume both event types from a durable notification queue
- Send invoice and payment emails through SMTP
- Deduplicate notifications by event ID and persist delivery status
- Retry failed SMTP deliveries three times with exponential backoff
- Protect API operations with an `X-API-Key` header
- Expose OpenAPI documentation and health/metrics endpoints

## Technology

- Java 17
- Spring Boot 2.7.18
- Spring MVC, Data JPA, Security, AMQP, Validation, and Actuator
- PostgreSQL and Flyway
- RabbitMQ publisher confirms and consumer queues
- SMTP email delivery with JavaMail
- JUnit 5, MockMvc, Awaitility, and Testcontainers
- Mailpit for local email inspection
- Docker and Docker Compose

## Run with Docker Compose

```bash
docker compose up --build
```

The API listens on `http://localhost:8080`, and the notification service exposes health on `http://localhost:8081/actuator/health`. RabbitMQ management is available on `http://localhost:15672` with the local development credentials `guest` / `guest`. Mailpit's received-email interface is available on `http://localhost:8025`.

Check application health:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
```

OpenAPI documentation:

- `http://localhost:8080/swagger-ui.html`
- `http://localhost:8080/v3/api-docs`

Stop the application and dependencies with `Ctrl+C`, then:

```bash
docker compose down
```

The development API key is `dev-invoiceflow-key`. Set a strong `INVOICEFLOW_API_KEY` outside local development.

## Exercise the product

Create an invoice:

```bash
export BASE_URL=http://localhost:8080
export API_KEY=dev-invoiceflow-key

CREATE_RESPONSE=$(curl --fail --silent --show-error \
  -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "7a4ce2c4-5a63-4a76-bf35-e1631c2641a8",
    "customerEmail": "customer@example.com",
    "currency": "USD",
    "dueDate": "2099-12-31",
    "lines": [
      {"description": "Production readiness assessment", "quantity": 1, "unitPrice": 100.00},
      {"description": "Migration verification", "quantity": 1, "unitPrice": 50.00}
    ]
  }' \
  "$BASE_URL/api/v1/invoices")

printf '%s\n' "$CREATE_RESPONSE" | jq .
INVOICE_ID=$(printf '%s' "$CREATE_RESPONSE" | jq -r .id)
```

Issue it:

```bash
curl --fail --silent --show-error \
  -X POST -H "X-API-Key: $API_KEY" \
  "$BASE_URL/api/v1/invoices/$INVOICE_ID/issue" | jq .
```

Record a partial payment:

```bash
curl --fail --silent --show-error \
  -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"paymentReference":"bank-transfer-001","amount":40.00}' \
  "$BASE_URL/api/v1/invoices/$INVOICE_ID/payments" | jq .
```

Record the remaining balance:

```bash
curl --fail --silent --show-error \
  -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"paymentReference":"bank-transfer-002","amount":110.00}' \
  "$BASE_URL/api/v1/invoices/$INVOICE_ID/payments" | jq .
```

Read the invoice and list invoices:

```bash
curl --fail --silent --show-error \
  -H "X-API-Key: $API_KEY" \
  "$BASE_URL/api/v1/invoices/$INVOICE_ID" | jq .

curl --fail --silent --show-error \
  -H "X-API-Key: $API_KEY" \
  "$BASE_URL/api/v1/invoices?page=0&size=20&sort=createdAt,desc" | jq .
```

Repeat one payment request with the same reference and amount. The response remains successful, but no additional payment or outbox event is created. Reusing that reference with a different amount returns HTTP 409.

## Events

The durable topic exchange is `invoice.events`. Routing keys are:

- `invoice.issued`
- `invoice.payment.recorded`

Each JSON message contains `eventId`, `eventType`, `aggregateId`, `occurredAt`, and a business `payload`. The RabbitMQ message ID equals the event ID.

Publication is at least once. A failure after broker acceptance but before the outbox update can produce a duplicate, so consumers must deduplicate by event ID or business reference. The relay preserves event order during a single run and stops at the first failed publication.

The notification service binds the durable `invoice.notifications` queue to both routing keys. It stores each event ID, recipient, delivery status, attempt count, and last error in the `notifications` PostgreSQL schema. A repeated event ID returns successfully without sending another email. SMTP failures are retried three times before the delivery is marked `FAILED`.

Docker Compose uses Mailpit, so emails stay on your machine. For another SMTP server, configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, and optionally `NOTIFICATION_FROM` on the notification service.

## Build and test locally

Requirements:

- Java 17; `.sdkmanrc` selects `17.0.16-tem`
- Maven 3.9 or newer
- Docker for integration tests

```bash
sdk env install
mvn --batch-mode clean verify
```

The build includes unit tests and real PostgreSQL/RabbitMQ integration tests covering:

- Invoice totals and state transitions
- Idempotent payment references
- Overpayment prevention
- API validation, authentication, and not-found responses
- Concurrent payments that cannot exceed the balance
- Confirmed outbox publication and event envelopes
- Real RabbitMQ consumption and SMTP delivery through Mailpit
- Duplicate notification suppression
- Retry exhaustion and persisted failed-delivery records

Unit tests run through Surefire. `*IT` integration tests run through Failsafe during `verify`.

Build the service containers directly:

```bash
docker build --build-arg SERVICE=invoiceflow-api --tag invoiceflow-api:local .
docker build --build-arg SERVICE=invoiceflow-notifications --tag invoiceflow-notifications:local .
```

## API errors

Errors use a consistent JSON problem shape with `status`, `title`, `detail`, `instance`, and `timestamp`. Validation failures also include field errors.

- `400` — invalid input or malformed JSON
- `401` — missing or invalid API key
- `404` — invoice not found
- `409` — invoice state or payment rule violation
- `500` — unexpected failure with no internal exception details exposed
