package com.ledger.service;

import com.ledger.dto.request.CreateAccountRequest;
import com.ledger.dto.response.AccountResponse;
import com.ledger.dto.response.BalanceResponse;
import com.ledger.dto.response.LedgerEventResponse;
import com.ledger.entity.Account;
import com.ledger.entity.LedgerEvent;
import com.ledger.entity.User;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;

    // Get currently logged in user from JWT context
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        User user = getCurrentUser();

        Account account = Account.builder()
                .ownerName(user.getFullName())
                .userId(user.getId())
                .accountType(request.getAccountType())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .build();

        account = accountRepository.save(account);
        log.info("Account created: {} for user: {}", account.getId(), user.getEmail());

        return toResponse(account, BigDecimal.ZERO);
    }

    public List<AccountResponse> getMyAccounts() {
        User user = getCurrentUser();
        return accountRepository.findByUserId(user.getId())
                .stream()
                .map(account -> {
                    BigDecimal balance = ledgerService.computeCurrentBalance(account.getId());
                    return toResponse(account, balance);
                })
                .collect(Collectors.toList());
    }

    public AccountResponse getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        BigDecimal balance = ledgerService.computeCurrentBalance(accountId);
        return toResponse(account, balance);
    }

    // Current derived balance
    public BalanceResponse getCurrentBalance(UUID accountId) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        BigDecimal balance = ledgerService.computeCurrentBalance(accountId);

        return BalanceResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .asOf(LocalDateTime.now())
                .note("Balance derived from complete event history")
                .build();
    }

    // Point-in-time balance — the killer feature
    public BalanceResponse getBalanceAsOf(UUID accountId, LocalDateTime asOf) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        BigDecimal balance = ledgerService.computeBalanceAsOf(accountId, asOf);

        return BalanceResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .asOf(asOf)
                .note("Balance as of " + asOf + " — computed by replaying events up to this point")
                .build();
    }

    // Full audit trail
    public List<LedgerEventResponse> getEventHistory(UUID accountId) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        return ledgerService.getEventHistory(accountId)
                .stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    // Statement for date range
    public List<LedgerEventResponse> getStatement(UUID accountId,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        return ledgerService.getEventsInRange(accountId, from, to)
                .stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    private AccountResponse toResponse(Account account, BigDecimal balance) {
        return AccountResponse.builder()
                .id(account.getId())
                .ownerName(account.getOwnerName())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .currentBalance(balance)
                .createdAt(account.getCreatedAt())
                .build();
    }

    private LedgerEventResponse toEventResponse(LedgerEvent event) {
        return LedgerEventResponse.builder()
                .id(event.getId())
                .accountId(event.getAccountId())
                .transactionId(event.getTransactionId())
                .eventType(event.getEventType())
                .amount(event.getAmount())
                .balanceAfter(event.getBalanceAfter())
                .description(event.getDescription())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
