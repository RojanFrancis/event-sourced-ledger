-- Adds the balance_after >= 0 CHECK constraint to an existing ledger_events table.
-- New databases get it automatically from the @Check on LedgerEvent; Hibernate's
-- ddl-auto=update does not add constraints to a table that already exists.
--
-- Run each step in order in the TiDB Cloud SQL editor (or any MySQL 8.0.16+ client).

-- 1. Make sure no existing row would break the constraint. Must return 0 rows.
SELECT HEX(account_id) AS account, created_at, balance_after
FROM ledger_events
WHERE balance_after < 0;

-- 2. TiDB only. CHECK constraints are parsed but NOT enforced unless this is ON
--    (it is OFF by default). Constraints added while it is OFF stay unenforced,
--    so turn it on first. Skip this line on MySQL.
SET GLOBAL tidb_enable_check_constraint = ON;

-- 3. Add the constraint.
ALTER TABLE ledger_events
    ADD CONSTRAINT chk_balance_after_non_negative CHECK (balance_after >= 0);

-- 4. Prove the database enforces it: this insert must fail with
--    "Check constraint 'chk_balance_after_non_negative' is violated."
--    If it succeeds instead, the constraint is not enforced; the ROLLBACK undoes it.
BEGIN;
INSERT INTO ledger_events
    (id, account_id, transaction_id, event_type, amount, balance_after,
     description, idempotency_key, created_at)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), UNHEX(REPLACE(UUID(), '-', '')),
     UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 300.0000, -200.0000,
     'constraint check', CONCAT('constraint-check-', UUID()), NOW(6));
ROLLBACK;
