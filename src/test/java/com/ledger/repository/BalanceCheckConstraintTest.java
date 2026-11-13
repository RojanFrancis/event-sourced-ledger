package com.ledger.repository;

import com.ledger.entity.LedgerEvent;
import com.ledger.enums.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database must refuse a ledger event that leaves an account below zero,
 * even when the write bypasses LedgerService's own funds check.
 */
@DataJpaTest
class BalanceCheckConstraintTest {

    @Autowired
    private LedgerEventRepository repository;

    @Test
    void rejectsEventWithNegativeBalanceAfter() {
        LedgerEvent overdraft = event(new BigDecimal("-200.0000"));

        assertThatThrownBy(() -> repository.saveAndFlush(overdraft))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHK_BALANCE_AFTER_NON_NEGATIVE");
    }

    @Test
    void acceptsEventThatLeavesBalanceAtExactlyZero() {
        LedgerEvent saved = repository.saveAndFlush(event(BigDecimal.ZERO));

        assertThat(saved.getId()).isNotNull();
    }

    private static LedgerEvent event(BigDecimal balanceAfter) {
        return LedgerEvent.builder()
                .accountId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .eventType(EventType.DEBIT)
                .amount(new BigDecimal("300.0000"))
                .balanceAfter(balanceAfter)
                .description("constraint test")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }
}
