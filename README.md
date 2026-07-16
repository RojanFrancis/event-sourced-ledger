# Event-Sourced Ledger

A fintech-grade payment ledger backend built with Spring Boot — models money the way real banks do.

Every balance change is recorded as an **immutable, append-only event**. Balances are never stored directly — they are always **derived by replaying event history**. This guarantees a complete audit trail and enables point-in-time balance queries that are impossible with traditional CRUD systems.

---

## Why Event Sourcing?

In a traditional system:
```sql
UPDATE accounts SET balance = balance - 500 WHERE id = ?
```
If a bug corrupts this value, **the money is gone and the history is lost.**

In this system:
```sql
INSERT INTO ledger_events (account_id, event_type, amount, ...) VALUES (...)
```
Events are **immutable truth**. Balance can always be recomputed from history. Bugs can be detected and corrected without data loss.

---

## Core Concepts Implemented

| Concept | Implementation |
|---|---|
| **Event Sourcing** | Every balance change is an immutable append-only `LedgerEvent` |
| **Double-Entry Bookkeeping** | Every transfer creates exactly one DEBIT + one CREDIT event summing to zero |
| **Derived Balance** | `computeCurrentBalance()` replays all events — no balance column exists |
| **Point-in-Time Queries** | `GET /api/accounts/{id}/balance?asOf=2025-01-01` — replay events up to any timestamp |
| **Idempotency** | Unique idempotency keys prevent duplicate transactions on retries |
| **Overdraft Protection** | Debit operations validate sufficient funds atomically before recording |
| **Concurrency Safety** | `@Transactional` ensures debit + credit either both succeed or both rollback |
| **Audit Trail** | Full immutable event history per account with human-readable explanation |

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 25, Spring Boot 3.2 |
| **Database** | MySQL 8.0 |
| **ORM** | Spring Data JPA / Hibernate |
| **Auth** | Spring Security + JWT |
| **Frontend** | React.js + Axios + TailwindCSS |
| **Containerization** | Docker + Docker Compose |
| **Deployment** | Railway |
| **Build Tool** | Maven |

---

## API Reference

### Auth
```
POST   /api/auth/register          Register new user
POST   /api/auth/login             Login → returns JWT token
```

### Accounts
```
POST   /api/accounts               Create account
GET    /api/accounts               List my accounts
GET    /api/accounts/{id}          Get account details
GET    /api/accounts/{id}/balance              Current derived balance
GET    /api/accounts/{id}/balance?asOf=DATE    Point-in-time balance
GET    /api/accounts/{id}/events               Full event history (audit trail)
GET    /api/accounts/{id}/statement?from=&to=  Statement for date range
```

### Transactions
```
POST   /api/transactions/transfer   Transfer between accounts
POST   /api/transactions/deposit    Deposit into account
POST   /api/transactions/withdraw   Withdraw from account
GET    /api/transactions/{id}       Get transaction status
```

---

## Database Design

The key design decision: **`accounts` table has no `balance` column.**

```sql
-- accounts: identity only, no mutable balance
CREATE TABLE accounts (
    id           CHAR(36)     PRIMARY KEY,
    owner_name   VARCHAR(255) NOT NULL,
    account_type ENUM('SAVINGS','CURRENT','CREDIT') NOT NULL,
    currency     VARCHAR(3)   DEFAULT 'INR',
    status       ENUM('ACTIVE','FROZEN','CLOSED') DEFAULT 'ACTIVE',
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ledger_events: append-only, never updated or deleted
CREATE TABLE ledger_events (
    id               CHAR(36)       PRIMARY KEY,
    account_id       CHAR(36)       NOT NULL,
    transaction_id   CHAR(36)       NOT NULL,
    event_type       ENUM('CREDIT','DEBIT') NOT NULL,
    amount           DECIMAL(19,4)  NOT NULL,
    balance_after    DECIMAL(19,4)  NOT NULL,
    description      VARCHAR(500),
    idempotency_key  VARCHAR(255)   UNIQUE NOT NULL,
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_account_id (account_id),
    INDEX idx_created_at (created_at)
);
```

---

## Running Locally

### Prerequisites
- Java 25+
- Maven 3.9+
- MySQL 8.0 (or Docker)

### Option 1 — Docker (Recommended)
```bash
git clone https://github.com/yourusername/event-sourced-ledger.git
cd event-sourced-ledger
docker-compose up --build
```
App runs at `http://localhost:8080`

### Option 2 — Manual
```bash
# 1. Create MySQL database
mysql -u root -p
CREATE DATABASE ledger_db;

# 2. Update application.properties with your MySQL credentials

# 3. Run the application
mvn spring-boot:run
```

---

## Example: Transfer Flow

```bash
# 1. Register and login
POST /api/auth/register
{ "fullName": "Rojan Francis", "email": "rojan@example.com", "password": "pass123" }

POST /api/auth/login
→ returns: { "token": "eyJ..." }

# 2. Create two accounts
POST /api/accounts
Authorization: Bearer eyJ...
{ "accountType": "SAVINGS" }

# 3. Deposit money into Account A
POST /api/transactions/deposit
{ "accountId": "uuid-a", "amount": 5000, "idempotencyKey": "dep-001" }

# 4. Transfer from A to B
POST /api/transactions/transfer
{
  "fromAccountId": "uuid-a",
  "toAccountId": "uuid-b",
  "amount": 1500,
  "description": "Rent payment",
  "idempotencyKey": "txn-001"
}

# 5. Check Account A balance (derived from events)
GET /api/accounts/uuid-a/balance
→ { "balance": 3500.0000 }

# 6. Point-in-time query
GET /api/accounts/uuid-a/balance?asOf=2025-01-01T00:00:00
→ { "balance": 0.0000 }  ← before any events existed

# 7. Full audit trail
GET /api/accounts/uuid-a/events
→ [ { "eventType": "CREDIT", "amount": 5000, "balanceAfter": 5000, ... },
    { "eventType": "DEBIT",  "amount": 1500, "balanceAfter": 3500, ... } ]
```

---

## Key Engineering Decisions

**Why UUIDs instead of auto-increment IDs?**
UUIDs are safe to generate client-side, don't leak record counts, and work across distributed systems — all important in financial infrastructure.

**Why `DECIMAL(19,4)` for money?**
Floating point (`double`, `float`) cannot represent all decimal values exactly. `0.1 + 0.2 = 0.30000000000000004` in floating point. Financial systems always use `DECIMAL` or `BigDecimal`.

**Why idempotency keys?**
Networks fail. Clients retry. Without idempotency keys, a retry could double-charge a customer. Every transaction carries a unique key — if it's been processed before, return the existing result instead of processing again.

**Why `@Transactional` on transfers?**
A transfer involves two ledger writes (debit + credit). If the credit fails after the debit succeeds, money disappears. `@Transactional` wraps both writes in one atomic database transaction — both succeed or both rollback.

---

## Project Structure

```
src/main/java/com/ledger/
├── controller/       REST API endpoints
├── service/          Business logic (LedgerService, TransactionService)
├── repository/       Data access layer (Spring Data JPA)
├── entity/           JPA entities (Account, LedgerEvent, Transaction, User)
├── dto/              Request and response objects
├── enums/            AccountType, EventType, TransactionStatus
├── exception/        Custom exceptions + GlobalExceptionHandler
└── security/         JWT filter, UserDetailsService
```

---

## What I Learned Building This

- How real financial systems model money using event sourcing instead of mutable balances
- Double-entry bookkeeping — every transaction must balance to zero across accounts
- Idempotency as a first-class API design concern
- ACID transactions and why they matter when multiple writes must be atomic
- The difference between `BigDecimal` and floating point for financial calculations
- Point-in-time queries — a capability that is structurally impossible in CRUD systems

---

## Roadmap

- [x] Core event sourcing engine
- [x] Double-entry transfers with atomic rollback
- [x] JWT authentication
- [x] Point-in-time balance queries
- [x] Idempotency on all write operations
- [x] Full audit trail API
- [ ] Account snapshots for performance optimization
- [ ] Fraud detection layer (rule-based transaction scoring)
- [ ] Multi-currency support

---

## Author

**Rojan Francis**
B.Tech Computer and Communication Engineering — Manipal University Jaipur
[LinkedIn](https://linkedin.com/in/yourprofile) · [GitHub](https://github.com/yourusername)
