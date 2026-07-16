package com.ledger.repository;

import com.ledger.entity.LedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEvent, UUID> {

    // All events for an account — ordered oldest to newest
    List<LedgerEvent> findByAccountIdOrderByCreatedAtAsc(UUID accountId);

    // Events up to a specific point in time — for point-in-time balance queries
    List<LedgerEvent> findByAccountIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            UUID accountId, LocalDateTime asOf);

    // Events within a date range — for statement generation
    List<LedgerEvent> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID accountId, LocalDateTime from, LocalDateTime to);

    // Get the latest event to get latest balance_after snapshot
    Optional<LedgerEvent> findTopByAccountIdOrderByCreatedAtDesc(UUID accountId);

    // Check idempotency — has this exact event been processed before?
    boolean existsByIdempotencyKey(String idempotencyKey);

    // All events linked to a specific transaction
    List<LedgerEvent> findByTransactionId(UUID transactionId);

    // Count events for an account — useful for pagination later
    long countByAccountId(UUID accountId);
}
