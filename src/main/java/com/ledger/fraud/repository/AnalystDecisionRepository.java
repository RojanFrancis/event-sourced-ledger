package com.ledger.fraud.repository;

import com.ledger.fraud.entity.AnalystDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AnalystDecisionRepository extends JpaRepository<AnalystDecision, UUID> {

    List<AnalystDecision> findByFlagIdOrderByCreatedAtAsc(UUID flagId);

    List<AnalystDecision> findByFlagIdInOrderByCreatedAtAsc(Collection<UUID> flagIds);
}
