
# Payment-Microservices


## Motivation

A project with a focus on **wiring components together** under one topic - banking transfers.

The goal is **not** a production-ready API with full CRUD, admin panel, or frontend.
The goal is to **integrate several components in combination** and see how they behave together:

OAuth2 with a custom JWT claim -> asynchronous processing via RabbitMQ ->
idempotency at multiple levels -> pessimistic locking and deadlock prevention ->
SSE for real-time push.

Each component is small. The interesting part is the combination.

## Architecture 

The system consists of three services, each with its own PostgreSQL database, plus shared infrastructure.

```mermaid
sequenceDiagram
    Client->>Auth Server: OAuth2 Login
    Auth Server-->>Client: JWT Token
    Client->>Payment Service: POST /transfers (idempotent)
    Payment Service->>Database: Lock Accounts (ASC by ID)
    Payment Service->>RabbitMQ: Publish TransferAddedEvent
    Payment Service-->>Client: {"status":"PENDING"}
    RabbitMQ->>Payment Service: TransferAddedEvent
    Payment Service->>Payment Service: Process (with lock)
    Payment Service->>RabbitMQ: Publish TransferProcessedEvent
    RabbitMQ->>Notification Service: TransferProcessedEvent
    Notification Service->>Client: SSE Push (COMPLETED)
```

**Services:**

- **auth-server** (`:9000`) - Spring Authorization Server. Issues JWT tokens, stores registered clients and authorizations in `auth_db` (`:5433`).
- **payment-service** (`:8080`) - main business logic. Handles accounts and transfers, uses `payment_db` (`:5435`) and Redis for caching, publishes transfer events to RabbitMQ.
- **notification-service** (`:8081`) - consumes TransferProcessedEvent from 
  RabbitMQ, stores notifications in notification_db (`:5436`), and pushes 
  them to clients via SSE.

**Infrastructure:**

- **RabbitMQ** (`:5672`, UI `:15672`) - async messaging between payment-service and notification-service.
- **Redis** (`:6379`) - cache for payment-service.
- **PostgreSQL** - three separate instances (one per service).

**Flow:**

1. Client authenticates via OAuth2 (authorization code flow) - payment-service redirects to auth-server, receives JWT.
2. Client calls payment-service REST API with JWT.
3. payment-service creates a transfer (PENDING), publishes a `TransferAddedEvent` to `processing.exchange` after transaction commit.
4. payment-service's listener consumes `TransferAddedEvent` from `processing.queue`,
   processes the transfer (via `TransferProcessor`), publishes `TransferProcessedEvent` to `payment.exchange`.
5. notification-service consumes `TransferProcessedEvent` from 
   `notification.queue` and pushes the result to the client via SSE.

## Technologies

  - Java 21, Spring Boot 4, Spring Data JPA, Spring Security, OAuth2 <br>
  - PostgreSQL, Redis (Spring Cache), RabbitMQ <br>
  - Testcontainers, JUnit 5, Mockito <br>
  - Docker, Docker Compose <br>
    
## Quick start

### Prerequisites
- Docker + Docker Compose
- Java 21 (if running services locally)

### Run the full stack

```bash
# Clone the repository
git clone https://github.com/Egrius/payment-microservices

# Change the directory
cd payment-microservices

# Create .env from the template and set your own values
cp .env.example .env
# Open .env and change DB_PASSWORD, AUTH_DB_PASSWORD, etc.

# Start everything
docker compose -f ./docker-compose.yaml --env-file ./.env up --build
```

> **Note**: `.env` is not committed to the repository. Use `.env.example` as a template.

> **Note**: On the first `docker compose up`, `payment-service` may restart
> once while waiting for `auth-server` to become fully ready.
> This is expected; `restart: on-failure` handles it automatically.

### Endpoints

Once the stack is up:

- **payment-service**: http://localhost:8080
- **auth-server**: http://localhost:9000
- **notification-service**: http://localhost:8081
- **RabbitMQ Management UI**: http://localhost:15672 (guest / guest)

API documentation (Swagger UI):

- payment-service: http://localhost:8080/swagger-ui.html
- notification-service: http://localhost:8081/swagger-ui.html

> For local testing: `POST /dev/token` (active only with the `dev` profile)
> returns a JWT for the seeded test user without the OAuth2 authorization code flow.

Health checks:

- http://localhost:8080/actuator/health
- http://localhost:9000/actuator/health

### Run tests

```bash
./gradlew test
```

> **Note**: Integration tests use Testcontainers (Docker must be running).

### Rebuild a module without tests

```bash
./gradlew build -x test
```

## What's interesting inside

### 1. Idempotency
There are two levels of idempotency in this project. They solve different problems and work in different levels.

#### Level 1 - HTTP request idempotency (`Idempotency-Key`)

`POST /api/transfers` requires an `Idempotency-Key` header (UUID). The server guarantees that a retry with the same key never creates a second transfer and never returns a different result. 

The flow is built around `tryInsert()` in `IdempotencyService` class. `tryInsert()` method uses sql `INSERT ... ON CONFLICT (value, user_id) DO NOTHING`.

1. Compute a SHA-256 hash of the request body (requestHash).

2. Attempt tryInsert(key, userId, requestHash). This is race-free: concurrent requests with the same key cannot both insert.
  
3. If the insert succeeded (isNew = true), then process the request normally. On commit, store the response in the key (complete(..., 201, responseBody, null)). On a deterministic failure (validation, insufficient funds) - store the error response. On a transient failure - release the key (release(...)) so the next retry can proceed.

4. If the key already existed (isNew = false) then check:
  - requestHash mismatch -> `IdempotencyKeyMismatchException` (422). Same key, different body - client bug.
  - responseStatus == null -> `TransferInProgressException` (409). The first request is still in flight.
  - responseStatus >= 400 -> `IdempotentReplayException` with the stored error body.
  - Otherwise return the stored successful response body.
  
5. `complete(...)` and `release(...)` run in REQUIRES_NEW so they survive a rollback of the main transaction. The whole mechanism is client-driven: the server does not generate the key.

#### Level 2 - RabbitMQ message deduplication

Transfers are processed asynchronously. Repeated RabbitMQ message delivery
does not lead to double debit: `TransferProcessor` loads the transfer with a pessimistic
lock (`findByIdPessimistic()`), checks the status, and skips if it's already
`COMPLETED`. The pessimistic lock guarantees that only one concurrent
transaction can process the transfer at a time.

> `findByIdPessimistic()` -  is a repository method with `@Lock(PESSIMISTIC_WRITE)` - it acquires a row-level lock on the transfer, so concurrent consumers cannot process the same transfer in parallel.

On duplicates, `TransferProcessor` checks the status:
- `COMPLETED` -> log "already completed, skipping" and return. No side effects.
- Anything other than `PENDING` -> `TransferNotPendingException`.
- `PENDING` -> proceed with the actual transfer.

### 2. Transactions and external services
Events are published to RabbitMQ and SSE notifications are sent strictly after the transaction is committed, using `TransactionSynchronization.afterCommit`. <br>
This prevents a situation where a message is sent but the transaction is rolled back.

### 3. DLQ and Stateful Retry
Set up in RabbitMQ config:

- **Retry**: 3 attempts with `1000ms -> 2000ms -> 4000ms` backoff through `RetryInterceptorBuilder.stateful()`.
- **DLQ**:  `processing.queue` is set up with  `x-dead-letter-exchange = dead.letter.exchange`.
- **Publisher retry**: separate `RetryTemplate` in `RabbitTemplate`.

`TransferProcessor` distinguishes exceptions:

- **Deterministic** (`InsufficientFundsException`) -> `TransferFailureHandler.handle(...)` -> transfer `FAILED`, ACK. Retry is not needed.

- **Duplicate delivery** (`TransferNotPendingException`) -> transfer is already in a
  terminal state (e.g., `FAILED` after a previous attempt). The listener logs a warning
  and ACK the message. No retry, no state change. This is an expected outcome of
  at-least-once delivery, not an error.
  
- **Transient** (all other exceptions, including exceptions from `TransferFailureHandler` itself) -> **no interceptions** -> retry (3 attempts) -> DLQ.
  
There is no outer catch in `TransferProcessor`. When `TransferFailureHandler.handle(...)` itself throws, it wraps the cause in `TransferProcessingException`, which propagates out of the listener method and reaches the retry-advice.

### 4. Deadlock prevention
Accounts are locked during the transfer using pessimistic lock in ascending order of their IDs. <br>
Tested with concurrency tests.

### 5. Failure handling without breaking the main transaction
`TransferFailureHandler` uses `REQUIRED` propagation instead of 
`REQUIRES_NEW` because `REQUIRES_NEW` leads to a deadlock on the 
pessimistic lock on accounts, which is held by the main transaction.

The handler loads the Transfer itself (findById) and does not rely on the caller's 
persistence context - `findById` could return an already MANAGED entity 
if it was loaded earlier in the same transaction.

### 6. SSE
Active `SseEmitter` instances are stored in `ConcurrentHashMap`, batch sending of `PENDING` notifications when the connection is established, and cleanup on `onCompletion`/`onTimeout`/`onError`. Some extra notification statuses may be used in the future to guarantee the delivery.

## Tests

Tests demonstrate the patterns actually work:

- **`TransferIdempotencyTests`** - HTTP-level idempotency:
  
  - Same `Idempotency-Key` -> same response, no duplicate transfer.
  - Same key, different body -> 422.
  - Replay of error response.
  - 5 concurrent requests with the same key -> exactly 1 transfer.
    
- **`TransferServiceConcurrencyTests`** - deadlock prevention and concurrency:
  
  - 10 parallel transfers with insufficient funds -> expected success/failure split, balances consistent.
  - 2 threads transferring in opposite directions -> no deadlock.
  - Duplicate processing of the same transfer -> skipped gracefully.
    
- **`TransferServiceCacheIntegrationTests`** - cache-aside behavior:
  
  - Cache miss -> DB, cache hit -> no DB.
  - `evict` on transfer processing (success and failure).
- **`StatefulRetryTest`** - infrastructure:
  - Listener fails -> retry 3 times -> message ends up in DLQ.
    
- **Unit tests** for `TransferService`, `AccountService`, `TransferProcessor`.
  
Integration tests use Testcontainers (PostgreSQL, Redis, RabbitMQ).

## Smoke test

The databases are seeded on first start: a test user in `auth_db` and two accounts in `payment_db`. All steps except SSE can be done from Swagger UI.

### Pre-seeded data

**User** (auth-server):
```
email:    smoke@test.com
password: password123
public_id: 11111111-1111-1111-1111-111111111111
```

**Accounts** (payment-service, owned by the user above):
```
Checking: 22222222-2222-2222-2222-222222222222  (1000.00 USD)
Savings:  33333333-3333-3333-3333-333333333333  (1000.00 USD)
```

Follow up the smoke test flow:

#### Step 1 - Get a JWT

- Open `http://localhost:8080/swagger-ui.html`.
- Find **Dev - Auth** and perform `POST /dev/token`.
- There is already an example value with test data (user from above). Press **Execute**.

Response: `200 OK`, copy the value from `access_token`.

> `/dev/token` is dev-only endpoint that's available with 'dev' profile for smoke test purpose.

#### Step 2 - Authorize in Swagger

- Use `Authorize` button at the right top corner.
- Paste the token you've obtained **without** the `Bearer ` prefix.
- Now secured endpoints are available for you.

#### Step 3 - Create a transfer

- Find **Transfers** and perform `POST /api/transfers`.
- **Idempotency-Key** is already filled with `aaaaaaaa-...`.
- **Example Value** is already filled with seed-data (`22222222-...`, `33333333-...`, `20.00`).
- Press **Execute**.

Response: `201 Created`, `{"status":"PENDING"}`, copy the value from `publicId` (transfer's id),  you'll use in step 4.

#### Step 4 - Check async processing

- Wait for 1-2 seconds.
- Perform `GET /api/transfers/{publicId}` (use `publicId` from the **step 3**).
-  Status should be changed from `PENDING` to `COMPLETED`, and `processedAt` is populated.

> **What happens between steps 3 and 4:**  `payment-service` publishes `TransferAddedEvent` to `processing.queue`, `TransferProcessor` picks it up, updates the transfer and balances, and publishes `TransferProcessedEvent` to `payment.exchange`. `notification-service` consumes it from `notification.queue` and pushes to the client via SSE.

#### Step 5 - Verify idempotency

- Repeat the same `POST /api/transfers` with the exact same `Idempotency-Key` and request body (from the **step 3**).
- Response should be identical to step 3 (same `publicId`, same `status`)
- No second transfer is created.
- `GET /api/accounts` - balances are unchanged from after step 3 (`Checking` = 980.00, `Savings` = 1020.00).

#### Step 6 - SSE notification

The SSE endpoint is exposed at `notification-service` Swagger UI (`http://localhost:8081/swagger-ui.html`), but **Swagger UI does not support streaming responses** - the "Try it out" button will return an empty body. Use **Postman** or `curl -N` instead.

## Restrictions and compromises

### Consciously omitted: 

- Detailed API design (REST conventions, versioning) - the focus was on internal logic and trying new things in practice 
- Frontend - outside the scope
- Full CRUD and admin panel - not the goal

### afterCommit vs Outbox

Events are published in `TransactionSynchronization.afterCommit`. This is **not**
the Outbox pattern: if the JVM crashes between commit and publish, the event is
lost. For a pet-project, this is acceptable. For production, Outbox would be
required - but implementing it here would be excessive.

### Token delivery to the client

The current setup mixes OAuth2 client flow (stateful session) for login
and JWT (stateless) for `/api/**`. For local testing, `POST /dev/token`
(dev profile only) issues a JWT directly from email/password, bypassing the
authorization code flow. No session is involved - it's a dev-only shortcut.
The production path still goes through auth-server's `/oauth2/authorize`.

The current approach was chosen to keep the demo simple; the focus was on
backend patterns (idempotency, DLQ, deadlock prevention), not on frontend architecture.
