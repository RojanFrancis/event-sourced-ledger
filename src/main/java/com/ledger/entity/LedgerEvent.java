package com.ledger.entity;

import com.ledger.enums.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_events", indexes = {
        @Index(name = "idx_account_id", columnList = "account_id"),
        @Index(name = "idx_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Which account this event belongs to
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    // Links the debit and credit events of the same transfer together
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    // Always stored as a positive number
    // EventType tells you if it's in or out
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Snapshot of balance after this event — for quick reads
    // Derived balance can be recomputed from scratch if needed
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "description")
    private String description;

    // Prevents the same event from being recorded twice on retry
    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    // Events are IMMUTABLE — once written, never updated or deleted
    // This column is set once at insert time only
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
