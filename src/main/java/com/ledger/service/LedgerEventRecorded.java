package com.ledger.service;

import java.util.UUID;

/** Published when a ledger event is saved; listeners that need committed data use AFTER_COMMIT. */
public record LedgerEventRecorded(UUID ledgerEventId) {
}
