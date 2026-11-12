package com.ledger.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A flag raised by one rule version on one ledger event. Like ledger events, flags are
 * immutable: an analyst's decision is a separate {@link AnalystDecision}, never an update here.
 */
@Entity
@Table(name = "fraud_flags",
        indexes = {
                @Index(name = "idx_flag_account", columnList = "account_id"),
                @Index(name = "idx_flag_created", columnList = "created_at")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_flag_trigger_rule",
                columnNames = {"trigger_event_id", "rule_id", "rule_version"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "trigger_event_id", nullable = false, updatable = false)
    private UUID triggerEventId;

    @Column(name = "rule_id", nullable = false, updatable = false, length = 64)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    // Null for rule-only flags; set once the anomaly model contributes to a flag.
    @Column(name = "model_version", updatable = false, length = 64)
    private String modelVersion;

    @Column(name = "score", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal score;

    // One reason per line
    @Column(name = "reasons", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reasons;

    // Comma-separated transaction ids
    @Column(name = "evidence_transaction_ids", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String evidenceTransactionIds;

    // When the trigger event was recorded: the flag is evaluated as of this instant
    @Column(name = "as_of", nullable = false, updatable = false)
    private LocalDateTime asOf;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
