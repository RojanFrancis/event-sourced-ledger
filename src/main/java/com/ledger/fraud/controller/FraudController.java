package com.ledger.fraud.controller;

import com.ledger.fraud.dto.DecisionRequest;
import com.ledger.fraud.dto.FlagResponse;
import com.ledger.fraud.dto.ReplayResponse;
import com.ledger.fraud.service.FlagReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Analyst endpoints; SecurityConfig restricts /api/fraud/** to ROLE_ANALYST. */
@RestController
@RequestMapping("/api/fraud/flags")
@RequiredArgsConstructor
public class FraudController {

    private final FlagReviewService reviewService;

    @GetMapping
    public ResponseEntity<List<FlagResponse>> recentFlags() {
        return ResponseEntity.ok(reviewService.recentFlags());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlagResponse> getFlag(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getFlag(id));
    }

    @PostMapping("/{id}/decisions")
    public ResponseEntity<FlagResponse> decide(@PathVariable UUID id,
                                               @Valid @RequestBody DecisionRequest request,
                                               Authentication authentication) {
        return ResponseEntity.ok(reviewService.decide(id, request, authentication.getName()));
    }

    @GetMapping("/{id}/replay")
    public ResponseEntity<ReplayResponse> replay(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.replay(id));
    }
}
