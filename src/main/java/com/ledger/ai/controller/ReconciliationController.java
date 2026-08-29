package com.ledger.ai.controller;

import com.ledger.ai.dto.AgentQueryRequest;
import com.ledger.ai.dto.AgentQueryResponse;
import com.ledger.ai.dto.ReconciliationRequest;
import com.ledger.ai.dto.ReconciliationResponse;
import com.ledger.ai.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * POST /api/ai/reconcile
     *
     * Razorpay Track 04 — AI Finance Controller
     * Submit a batch of transactions for AI-powered reconciliation.
     * Returns match rate, exceptions, and AI explanation.
     */
    @PostMapping("/reconcile")
    public ResponseEntity<ReconciliationResponse> reconcile(
            @Valid @RequestBody ReconciliationRequest request) {

        log.info("Reconciliation request received for batch: {}", request.getBatchId());
        ReconciliationResponse response = reconciliationService.reconcile(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/ai/query
     *
     * Settlement Q&A Agent.
     * Ask any question about an account in plain English.
     * AI reads real ledger events and explains.
     *
     * Example: "Why does this account have a discrepancy?"
     *          "What happened to this account on Aug 15?"
     *          "Is the current balance correct?"
     */
    @PostMapping("/query")
    public ResponseEntity<AgentQueryResponse> queryAgent(
            @RequestBody AgentQueryRequest request) {

        log.info("AI agent query: account={}", request.getAccountId());
        AgentQueryResponse response = reconciliationService.queryAgent(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/ai/health
     * Quick health check to verify AI layer is running.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Finance Controller is active — powered by Gemini + Event-Sourced Ledger");
    }
}
