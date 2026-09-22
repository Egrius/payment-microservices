# Payment-Microservices
<br>
Multi-module system for banking transfers, divided into microservices:<br>
Transfer creation, async processing via RabbitMQ, real-time notifications with SSE, separate authentication server. <br>

## Motivation
<br>
Pet-project for practicing specific technologies and approaches.<br>
Main focus — not the API design but the internal logic: <br>
<br> 
  - OAuth2 with a separate authorization server (Spring Authorization Server) 
and custom JWT claims (public_id); <br>
  - Idempotent transfer processing with RabbitMQ (read "Restrictions and compromises"); <br>
  - Consistency between transactions, database, and RabbitMQ; <br>
  - Deadlock prevention for concurrent transfers; <br> 
- Failure handling: <code>REQUIRED</code> instead of <code>REQUIRES_NEW</code> 
to avoid a deadlock on the accounts' pessimistic lock.

## Architecture 

The system consists of three services, each with its own PostgreSQL database, plus shared infrastructure.

**Services:**

- **auth-server** (`:9000`) — Spring Authorization Server. Issues JWT tokens, stores registered clients and authorizations in `auth_db` (`:5433`).
- **payment-service** (`:8080`) — main business logic. Handles accounts and transfers, uses `payment_db` (`:5435`) and Redis for caching, publishes transfer events to RabbitMQ.
- **notification-service** (`:8081`) — consumes TransferProcessedEvent from 
  RabbitMQ, stores notifications in notification_db (`:5436`), and pushes 
  them to clients via SSE.

**Infrastructure:**

- **RabbitMQ** (`:5672`, UI `:15672`) — async messaging between payment-service and notification-service.
- **Redis** (`:6379`) — cache for payment-service.
- **PostgreSQL** — three separate instances (one per service).

**Flow:**

1. Client authenticates via OAuth2 (authorization code flow) — payment-service redirects to auth-server, receives JWT.
2. Client calls payment-service REST API with JWT.
3. payment-service creates a transfer (PENDING), publishes a `TransferAddedEvent` to RabbitMQ after transaction commit.
4. payment-service consumes TransferAddedEvent from processing.queue, 
   processes the transfer (via TransferProcessor), and publishes 
   TransferProcessedEvent to payment.exchange.
5. notification-service consumes TransferProcessedEvent from 
   notification.queue and pushes the result to the client via SSE.

## Technologies
  - Java 21, Spring Boot 4, Spring Data JPA, Spring Security, OAuth2 <br>
  - PostgreSQL, Redis (Spring Cache), RabbitMQ <br>
  - Testcontainers, JUnit 5, Mockito <br>
  - Docker, Docker Compose <br>
<br>

## Quick start

### Prerequisites
- Docker + Docker Compose
- Java 21 (if running services locally)

### Run the full stack

```bash
# Clone the repository
git clone https://github.com/Egrius/payment-microservices

# Change the directory
cd payment-microservices/payment-service

# Create .env from the template and set your own values
cp .env.example .env
# Open .env and change DB_PASSWORD, AUTH_DB_PASSWORD, etc.

# Start everything
docker compose -f ./docker-compose.yaml --env-file ./.env up -d
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

Health checks:
- http://localhost:8080/actuator/health
- http://localhost:9000/actuator/health

### Run tests

```bash
./gradlew test
```

> **Note**: Integration tests use Testcontainers (Docker must be running).
> Some tests may be flaky when run together because of Testcontainers startup ordering.
> Run them separately if needed.

### Rebuild a module without tests

```bash
./gradlew build -x test
```

## What's interesting inside

### 1. Idempotent transfer processing
Transfers are processed asynchronously. Repeated RabbitMQ message delivery
does not lead to double debit: transfer's status is checked, a conditional UPDATE (WHERE status = 'PENDING') is executed, and <code>TransferAlreadyProcessedException</code> is thrown on duplicates.

### 2. Transactions and external services
Events are published to RabbitMQ and SSE notifications are sent strictly after the transaction is committed, using TransactionSynchronization.afterCommit. <br>
This prevents a situation where a message is sent but the transaction is rolled back.
### 3. Deadlock prevention
Accounts are locked during the transfer using pessimistic lock in ascending order of their IDs. <br>
Tested with concurrency tests.

### 4. Failure handling without breaking the main transaction
<code>TransferFailureHandler</code> uses <code>REQUIRED</code> propagation instead of 
<code>REQUIRES_NEW</code> because <code>REQUIRES_NEW</code> leads to a deadlock on the 
pessimistic lock on accounts, which is held by the main transaction.

The handler loads the Transfer itself (findById) and does not rely on the caller's 
persistence context — <code>findById</code> could return an already MANAGED entity 
if it was loaded earlier in the same transaction.

### 5. SSE
Active <code>SseEmitter</code> instances are stored in <code>ConcurrentHashMap</code>, batch sending of <code>PENDING</code> notifications when the connection is established, and cleanup on <code>onCompletion</code>/<code>onTimeout</code>/<code>onError</code>. Some extra notification statuses may be used in the future to guarantee the delivery.

## Tests

- Unit tests for services and 'processor' <br>
- Slice tests for controllers (<code>@WebMvcTest</code>) <br>
- Integration tests with Testcontainers (PostgreSQL, Redis) <br> 
- Concurrency tests: 10 parallel transfers with insufficient funds; deadlock prevention (2 threads, opposite directions); idempotency on duplicate <br>
- Integration OAuth2-flow with full redirection following and CSRF <br>

## Restrictions and compromises

### 1. Consciously omitted: 
- Detailed API design (REST conventions, versioning) — the focus was on internal logic and trying new things in practice 
- Swagger/OpenAPI — not added
- Frontend — outside the scope

### 2. Idempotent transfer processing
As mentioned before — transfers are idempotent for RabbitMQ duplicated delivery. After a RabbitMQ message is delivered – the cache is checked to find the DTO. 
If no DTO is found, the database is checked. After that, transfer's status is checked: if it's <code>PENDING</code>, 
it is processed and the status is switched to <code>COMPLETED</code>. 
PostgreSQL UPDATE acquires a row-level lock by default, so only one concurrent transaction can change the status.<br>

This is less efficient than reading the transfer from the database with a 
pessimistic lock, because extra work is done (balances are updated and then 
rolled back).

### 3. Cache in failure handling
I intentionally added caching in some internals — it's not optimal, but it was a practice goal <br> 

## Plans
- Outbox pattern for guaranteed event delivery
- DLQ for unprocessed messages
- SSE notification for receiver, not only for the sender
- Features to practice SQL optimization
<br>

