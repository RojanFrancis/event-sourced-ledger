package com.ledger.fraud.repository;

import com.ledger.fraud.entity.FraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, UUID> {

    boolean existsByTriggerEventIdAndRuleIdAndRuleVersion(UUID triggerEventId, String ruleId, int ruleVersion);

    List<FraudFlag> findTop200ByOrderByCreatedAtDesc();

    List<FraudFlag> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    boolean existsByAccountIdAndRuleIdAndRuleVersionAndAsOfAfter(UUID accountId, String ruleId, int ruleVersion,
                                                                  LocalDateTime after);
}
