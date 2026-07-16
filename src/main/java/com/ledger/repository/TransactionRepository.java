package com.ledger.repository;

import com.ledger.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByFromAccountIdOrToAccountIdOrderByInitiatedAtDesc(
            UUID fromAccountId, UUID toAccountId);
}
