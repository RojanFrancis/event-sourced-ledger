package com.ledger.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An analyst's review of a flag, appended and never changed. It records which rule and model
 * versions the analyst was looking at, so the decision can be audited later.
 */
@Entity
@Table(name = "analyst_decisions", indexes = @Index(name = "idx_decision_flag", columnList = "flag_id, created_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalystDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "flag_id", nullable = false, updatable = false)
    private UUID flagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false, length = 16)
    private Decision decision;

    @Column(name = "analyst", nullable = false, updatable = false)
    private String analyst;

    @Column(name = "note", updatable = false, length = 1000)
    private String note;

    @Column(name = "rule_id", nullable = false, updatable = false, length = 64)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private int ruleVersion;

    @Column(name = "model_version", updatable = false, length = 64)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
