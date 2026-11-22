# InvoiceFlow

InvoiceFlow is a real invoice and payment workflow API built as ProofCommit's progressive-migration reference product. The baseline is Spring Boot 2.7.18 on Java 17, using `javax.*` APIs and infrastructure versions appropriate to that generation.

It creates draft invoices, issues them, records partial or full external payments, prevents overpayment, and publishes invoice events through a transactional outbox. It is an invoicing product, not a payment gateway: it records confirmed external payments and does not charge cards or move money.

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
- Protect API operations with an `X-API-Key` header
- Expose OpenAPI documentation and health/metrics endpoints

## Technology

- Java 17
- Spring Boot 2.7.18
- Spring MVC, Data JPA, Security, AMQP, Validation, and Actuator
- PostgreSQL and Flyway
- RabbitMQ publisher confirms
- JUnit 5, MockMvc, and Testcontainers
- Docker and Docker Compose

## Run with Docker Compose

```bash
docker compose up --build
```

The application listens on `http://localhost:8080`. RabbitMQ management is available on `http://localhost:15672` with the local development credentials `guest` / `guest`.

Check application health:

```bash
curl --fail http://localhost:8080/actuator/health
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

Unit tests run through Surefire. `*IT` integration tests run through Failsafe during `verify`.

Build the container directly:

```bash
docker build --tag invoiceflow:local .
```

## API errors

Errors use a consistent JSON problem shape with `status`, `title`, `detail`, `instance`, and `timestamp`. Validation failures also include field errors.

- `400` — invalid input or malformed JSON
- `401` — missing or invalid API key
- `404` — invoice not found
- `409` — invoice state or payment rule violation
- `500` — unexpected failure with no internal exception details exposed

## Progressive migration plan

Keep Java 17 throughout the initial migration track so framework changes remain isolated from Java-version changes.

```text
Spring Boot 2.7.18 → 3.0.x → 3.5.x → 4.1.x
```

At each stage:

1. Tag the verified baseline.
2. Capture Foundry inspection and verification evidence.
3. Preview the migration and review the patch.
4. Apply on a reviewable branch.
5. Run the complete `verify` lifecycle and real Docker workflow.
6. Record application-specific fixes and generalizable recipe gaps.
7. Generate before/after evidence before starting the next hop.

Initial commands:

```bash
proofcommit inspect . > before.json
proofcommit report before.json > assessment.md
proofcommit verify . --java-home "$HOME/.sdkman/candidates/java/17.0.16-tem" > baseline.json
proofcommit plan before.json --to-spring-boot 3.0 > migration-plan.md
```

A passing build does not prove production readiness. Each hop also requires configuration review and runtime verification.

## Operational boundaries

This is a functional reference product with real persistence and broker behavior, not a claim of production certification. Before an internet-facing or multi-instance deployment, add appropriate identity and authorization, customer management, refunds/credit notes, rate limiting, secrets management, TLS, backup/restore verification, tracing, alerting, capacity testing, and deployment runbooks.

The current outbox relay is suitable for a single application instance. Multi-instance relay execution requires coordinated claiming, such as `FOR UPDATE SKIP LOCKED`, and additional duplicate/recovery tests. API-key authentication is intended for service-to-service access, not end-user identity.
