# Event-Sourced Ledger — Project Structure

```
event-sourced-ledger/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── ledger/
│       │           ├── LedgerApplication.java
│       │           │
│       │           ├── config/
│       │           │   ├── SecurityConfig.java
│       │           │   └── JwtConfig.java
│       │           │
│       │           ├── controller/
│       │           │   ├── AccountController.java
│       │           │   ├── TransactionController.java
│       │           │   └── AuthController.java
│       │           │
│       │           ├── service/
│       │           │   ├── AccountService.java
│       │           │   ├── LedgerService.java
│       │           │   ├── TransactionService.java
│       │           │   └── AuthService.java
│       │           │
│       │           ├── repository/
│       │           │   ├── AccountRepository.java
│       │           │   ├── LedgerEventRepository.java
│       │           │   ├── TransactionRepository.java
│       │           │   └── UserRepository.java
│       │           │
│       │           ├── entity/
│       │           │   ├── Account.java
│       │           │   ├── LedgerEvent.java
│       │           │   ├── Transaction.java
│       │           │   └── User.java
│       │           │
│       │           ├── dto/
│       │           │   ├── request/
│       │           │   │   ├── CreateAccountRequest.java
│       │           │   │   ├── TransferRequest.java
│       │           │   │   ├── DepositRequest.java
│       │           │   │   ├── WithdrawRequest.java
│       │           │   │   └── LoginRequest.java
│       │           │   └── response/
│       │           │       ├── AccountResponse.java
│       │           │       ├── BalanceResponse.java
│       │           │       ├── TransactionResponse.java
│       │           │       ├── LedgerEventResponse.java
│       │           │       └── AuthResponse.java
│       │           │
│       │           ├── enums/
│       │           │   ├── AccountType.java
│       │           │   ├── AccountStatus.java
│       │           │   ├── EventType.java
│       │           │   └── TransactionStatus.java
│       │           │
│       │           ├── exception/
│       │           │   ├── GlobalExceptionHandler.java
│       │           │   ├── InsufficientFundsException.java
│       │           │   ├── AccountNotFoundException.java
│       │           │   └── DuplicateTransactionException.java
│       │           │
│       │           └── security/
│       │               ├── JwtUtil.java
│       │               ├── JwtFilter.java
│       │               └── UserDetailsServiceImpl.java
│       │
│       └── resources/
│           ├── application.properties
│           └── db/
│               └── schema.sql
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```
