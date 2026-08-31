# Event-Sourced Payment Ledger
 
A fintech-grade payment ledger built with Spring Boot — models money the way real banks do, now with an **AI Finance Controller** powered by Gemini.
 
> 🏆 **Submitted to Razorpay AI Builder Internship 2026 — Track 04: AI Finance Controller**
 
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
 
## AI Finance Controller (Track 04)
 
The AI layer sits on top of the event-sourced ledger and uses **Gemini 2.0 Flash** to provide intelligent financial analysis.
 
### Features
 
| Feature | Description |
|---|---|
| **Batch Reconciliation** | Gemini scans all ledger events to detect mismatches, duplicate entries, and balance anomalies across accounts |
| **Settlement Q&A Agent** | Ask plain-English questions about your settlement data — Gemini reads ledger context and responds with grounded answers |
 
### AI Endpoints
 
```
POST   /api/ai/reconcile     Run Gemini batch reconciliation over all ledger events
POST   /api/ai/ask           Ask the settlement Q&A agent a natural language question
```
 
### AI Package Structure
 
```
src/main/java/com/ledger/ai/
├── service/
│   ├── GeminiService.java           Gemini API integration (HTTP client)
│   └── ReconciliationService.java   Batch reconciliation logic
├── controller/
│   └── ReconciliationController.java  AI REST endpoints
└── dto/
    ├── ReconciliationRequest.java
    └── ReconciliationResponse.java
```
 
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
| **AI Reconciliation** | Gemini batch analysis over ledger events for anomaly detection |
| **Settlement Agent** | Natural language Q&A over settlement data via Gemini |
 
---
 
## Tech Stack
 
| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 3.5 |
| **AI** | Gemini 2.0 Flash (Google Generative AI) |
| **Database** | MySQL 8.0 |
| **ORM** | Spring Data JPA / Hibernate |
| **Auth** | Spring Security + JWT |
| **Frontend** | React + Vite + Axios |
| **Containerization** | Docker + Docker Compose |
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
GET    /api/accounts               List my accounts
POST   /api/accounts               Create account
GET    /api/accounts/{id}          Get account details
GET    /api/accounts/{id}/balance              Current derived balance
GET    /api/accounts/{id}/balance?asOf=DATE    Point-in-time balance
GET    /api/accounts/{id}/events               Full event history (audit trail)
```
 
### Transactions
 
```
POST   /api/transactions/deposit    Deposit into account
POST   /api/transactions/withdraw   Withdraw from account
POST   /api/transactions/transfer   Transfer between accounts
```
 
### AI Finance Controller
 
```
POST   /api/ai/reconcile   Run Gemini batch reconciliation over all ledger events
POST   /api/ai/ask         Ask the settlement Q&A agent a natural language question
```
 
---
 
## Running Locally
 
### Prerequisites
 
- Java 21+
- Maven 3.9+
- MySQL 8.0 (or Docker)
- Gemini API key ([get one here](https://aistudio.google.com/))
### Option 1 — Docker (Recommended)
 
```bash
git clone https://github.com/RojanFrancis/event-sourced-ledger.git
cd event-sourced-ledger
docker-compose up --build
```
 
App runs at `http://localhost:8080`
 
### Option 2 — Manual
 
```bash
# 1. Create MySQL database
mysql -u root -p
CREATE DATABASE ledger_db;
 
# 2. Set your Gemini API key in application.properties
# gemini.api.key=YOUR_KEY_HERE
 
# 3. Run the backend
mvn spring-boot:run
 
# 4. Run the frontend
cd frontend
npm install
npm run dev
```
 
Frontend runs at `http://localhost:5173`
 
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
 
## Project Structure
 
```
src/main/java/com/ledger/
├── ai/
│   ├── controller/       ReconciliationController
│   ├── service/          GeminiService, ReconciliationService
│   └── dto/              ReconciliationRequest, ReconciliationResponse
├── controller/           REST API endpoints
├── service/              LedgerService, TransactionService
├── repository/           Spring Data JPA repositories
├── entity/               Account, LedgerEvent, Transaction, User
├── dto/                  Request and response objects
├── enums/                AccountType, EventType, TransactionStatus
├── exception/            Custom exceptions + GlobalExceptionHandler
└── security/             JWT filter, UserDetailsService
 
frontend/src/
├── pages/
│   ├── AIController.jsx  AI Finance Controller (Batch Reconcile + Q&A Agent)
│   ├── Dashboard.jsx
│   ├── AccountDetail.jsx
│   ├── Login.jsx
│   └── Register.jsx
├── components/
│   └── Navbar.jsx
├── services/
│   └── api.js
└── context/
    └── AuthContext.jsx
```
 
---
 
## Roadmap
 
- [x] Core event sourcing engine
- [x] Double-entry transfers with atomic rollback
- [x] JWT authentication
- [x] Point-in-time balance queries
- [x] Idempotency on all write operations
- [x] Full audit trail API
- [x] Gemini AI — batch reconciliation
- [x] Gemini AI — settlement Q&A agent
- [x] React frontend dashboard
- [ ] Account snapshots for performance optimization
- [ ] Multi-currency support
---
 
## Author
 
**Rojan Francis**
B.Tech Computer and Communication Engineering — Manipal University Jaipur (2023–2027)
 
[GitHub](https://github.com/RojanFrancis) · [LinkedIn](https://linkedin.com/in/rojanfrancis)
 
