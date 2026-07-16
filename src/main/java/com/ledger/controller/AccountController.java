package com.ledger.controller;

import com.ledger.dto.request.CreateAccountRequest;
import com.ledger.dto.response.AccountResponse;
import com.ledger.dto.response.BalanceResponse;
import com.ledger.dto.response.LedgerEventResponse;
import com.ledger.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // Create account
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(accountService.createAccount(request));
    }

    // Get all my accounts
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        return ResponseEntity.ok(accountService.getMyAccounts());
    }

    // Get specific account
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    // Get balance — supports optional ?asOf= for point-in-time query
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {

        if (asOf != null) {
            // Point-in-time balance — the killer fintech feature
            return ResponseEntity.ok(accountService.getBalanceAsOf(id, asOf));
        }
        return ResponseEntity.ok(accountService.getCurrentBalance(id));
    }

    // Full event history — complete audit trail
    @GetMapping("/{id}/events")
    public ResponseEntity<List<LedgerEventResponse>> getEventHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getEventHistory(id));
    }

    // Statement for date range
    @GetMapping("/{id}/statement")
    public ResponseEntity<List<LedgerEventResponse>> getStatement(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(accountService.getStatement(id, from, to));
    }
}