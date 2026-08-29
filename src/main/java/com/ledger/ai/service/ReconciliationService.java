package com.ledger.ai.service;

import com.ledger.ai.dto.ReconciliationRequest;
import com.ledger.ai.dto.ReconciliationResponse;
import com.ledger.ai.dto.AgentQueryRequest;
import com.ledger.ai.dto.AgentQueryResponse;
import com.ledger.entity.LedgerEvent;
import com.ledger.repository.LedgerEventRepository;
import com.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final GeminiService geminiService;
    private final LedgerEventRepository ledgerEventRepository;
    private final LedgerService ledgerService;

    /**
     * CORE FEATURE — AI-powered batch reconciliation.
     *
     * Takes a batch of transaction records, uses double-entry
     * bookkeeping rules to check balance, then uses Gemini AI
     * to explain any exceptions found.
     *
     * This is what Razorpay's finance ops team does manually every day.
     * We're automating it with AI.
     */
    public ReconciliationResponse reconcile(ReconciliationRequest request) {
        log.info("Starting AI reconciliation for batch: {}", request.getBatchId());

        List<ReconciliationRequest.TransactionRecord> records = request.getRecords();

        // Step 1: Separate credits and debits
        List<ReconciliationRequest.TransactionRecord> credits = records.stream()
            .filter(r -> "CREDIT".equalsIgnoreCase(r.getType()))
            .collect(Collectors.toList());

        List<ReconciliationRequest.TransactionRecord> debits = records.stream()
            .filter(r -> "DEBIT".equalsIgnoreCase(r.getType()))
            .collect(Collectors.toList());

        // Step 2: Calculate totals
        BigDecimal totalCredits = credits.stream()
            .map(ReconciliationRequest.TransactionRecord::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebits = debits.stream()
            .map(ReconciliationRequest.TransactionRecord::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netBalance = totalCredits.subtract(totalDebits);

        // Step 3: Find matching pairs (each debit should have a credit)
        List<ReconciliationResponse.ExceptionDetail> exceptions = new ArrayList<>();
        Set<String> matchedTransactionIds = new HashSet<>();

        // Group by transactionId to find pairs
        Map<String, List<ReconciliationRequest.TransactionRecord>> byTxnId = records.stream()
            .collect(Collectors.groupingBy(r ->
                r.getTransactionId() != null ? r.getTransactionId() : UUID.randomUUID().toString()));

        int matchedCount = 0;

        for (Map.Entry<String, List<ReconciliationRequest.TransactionRecord>> entry : byTxnId.entrySet()) {
            List<ReconciliationRequest.TransactionRecord> pair = entry.getValue();

            boolean hasCredit = pair.stream().anyMatch(r -> "CREDIT".equalsIgnoreCase(r.getType()));
            boolean hasDebit = pair.stream().anyMatch(r -> "DEBIT".equalsIgnoreCase(r.getType()));

            if (hasCredit && hasDebit) {
                // Check amounts match
                BigDecimal creditAmt = pair.stream()
                    .filter(r -> "CREDIT".equalsIgnoreCase(r.getType()))
                    .map(ReconciliationRequest.TransactionRecord::getAmount)
                    .findFirst().orElse(BigDecimal.ZERO);

                BigDecimal debitAmt = pair.stream()
                    .filter(r -> "DEBIT".equalsIgnoreCase(r.getType()))
                    .map(ReconciliationRequest.TransactionRecord::getAmount)
                    .findFirst().orElse(BigDecimal.ZERO);

                if (creditAmt.compareTo(debitAmt) == 0) {
                    matchedCount += pair.size();
                    matchedTransactionIds.add(entry.getKey());
                } else {
                    // Amount mismatch — flag as exception
                    for (ReconciliationRequest.TransactionRecord r : pair) {
                        exceptions.add(ReconciliationResponse.ExceptionDetail.builder()
                            .transactionId(r.getTransactionId())
                            .accountId(r.getAccountId())
                            .type(r.getType())
                            .amount(r.getAmount())
                            .reason("Amount mismatch — credit ₹" + creditAmt + " vs debit ₹" + debitAmt)
                            .severity("HIGH")
                            .build());
                    }
                }
            } else {
                // Unmatched — either credit or debit without counterpart
                for (ReconciliationRequest.TransactionRecord r : pair) {
                    String reason = hasCredit
                        ? "Credit entry has no matching debit — possible duplicate deposit or missing withdrawal"
                        : "Debit entry has no matching credit — possible missing deposit or unauthorized withdrawal";

                    exceptions.add(ReconciliationResponse.ExceptionDetail.builder()
                        .transactionId(r.getTransactionId())
                        .accountId(r.getAccountId())
                        .type(r.getType())
                        .amount(r.getAmount())
                        .reason(reason)
                        .severity(r.getAmount().compareTo(new BigDecimal("10000")) > 0 ? "HIGH" : "MEDIUM")
                        .build());
                }
            }
        }

        int exceptionCount = exceptions.size();
        int totalRecords = records.size();
        double matchRateVal = totalRecords > 0
            ? ((double)(totalRecords - exceptionCount) / totalRecords) * 100 : 0;
        String matchRate = String.format("%.1f%%", matchRateVal);
        String status = netBalance.compareTo(BigDecimal.ZERO) == 0 ? "BALANCED" : "UNBALANCED";

        // Step 4: Ask Gemini AI to explain the batch and exceptions
        String aiSummary = generateAiSummary(
            request.getBatchId(), totalRecords, matchedCount,
            exceptionCount, totalCredits, totalDebits,
            netBalance, status, exceptions);

        log.info("Reconciliation complete: batch={}, matched={}, exceptions={}, status={}",
            request.getBatchId(), matchedCount, exceptionCount, status);

        return ReconciliationResponse.builder()
            .batchId(request.getBatchId())
            .totalRecords(totalRecords)
            .matchedCount(totalRecords - exceptionCount)
            .exceptionCount(exceptionCount)
            .matchRate(matchRate)
            .totalCredits(totalCredits)
            .totalDebits(totalDebits)
            .netBalance(netBalance)
            .status(status)
            .exceptions(exceptions)
            .aiSummary(aiSummary)
            .processedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .build();
    }

    /**
     * SETTLEMENT Q&A — Ask AI about any account in plain English.
     *
     * This is where the magic happens for Razorpay judges —
     * you ask "why does account X have a discrepancy?" and
     * the AI reads the actual ledger events and explains.
     */
    public AgentQueryResponse queryAgent(AgentQueryRequest request) {
        log.info("AI agent query for account: {} — {}", request.getAccountId(), request.getQuestion());

        // Fetch real ledger events for this account
        List<LedgerEvent> events;
        try {
            java.util.UUID accountId = java.util.UUID.fromString(request.getAccountId());
            events = ledgerEventRepository.findByAccountIdOrderByCreatedAtAsc(accountId);
        } catch (Exception e) {
            events = new ArrayList<>();
        }

        // Build context from real events
        StringBuilder eventContext = new StringBuilder();
        eventContext.append("Account ID: ").append(request.getAccountId()).append("\n");
        eventContext.append("Total events: ").append(events.size()).append("\n\n");

        BigDecimal runningBalance = BigDecimal.ZERO;
        for (LedgerEvent event : events) {
            if ("CREDIT".equals(event.getEventType().name())) {
                runningBalance = runningBalance.add(event.getAmount());
            } else {
                runningBalance = runningBalance.subtract(event.getAmount());
            }
            eventContext.append(String.format("[%s] %s ₹%s | Balance after: ₹%s | %s\n",
                event.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                event.getEventType(),
                event.getAmount(),
                event.getBalanceAfter(),
                event.getDescription() != null ? event.getDescription() : ""));
        }
        eventContext.append("\nFinal computed balance: ₹").append(runningBalance);

        // Build AI prompt
        String prompt = """
            You are an AI Finance Controller analyzing a payment ledger system.
            
            Here is the complete transaction history for the account:
            
            %s
            
            The user asks: "%s"
            
            Please provide:
            1. A direct answer to the question
            2. Your reasoning based on the ledger events
            3. Any anomalies or concerns you notice
            4. Recommendations if applicable
            
            Keep your response clear, concise, and focused on financial accuracy.
            """.formatted(eventContext.toString(), request.getQuestion());

        String aiAnswer = geminiService.ask(prompt);

        return AgentQueryResponse.builder()
            .accountId(request.getAccountId())
            .question(request.getQuestion())
            .answer(aiAnswer)
            .reasoning("Based on " + events.size() + " immutable ledger events")
            .dataUsed("Complete event history from event-sourced ledger")
            .build();
    }

    /**
     * Generate AI summary for the reconciliation batch.
     */
    private String generateAiSummary(String batchId, int total, int matched,
                                      int exceptions, BigDecimal credits,
                                      BigDecimal debits, BigDecimal net,
                                      String status, List<ReconciliationResponse.ExceptionDetail> exList) {

        StringBuilder exceptionText = new StringBuilder();
        for (ReconciliationResponse.ExceptionDetail ex : exList) {
            exceptionText.append(String.format("- TxnID: %s | Type: %s | Amount: ₹%s | Reason: %s\n",
                ex.getTransactionId(), ex.getType(), ex.getAmount(), ex.getReason()));
        }

        String prompt = """
            You are an AI Finance Controller at a fintech company.
            Analyze this batch reconciliation and provide a brief executive summary:
            
            Batch ID: %s
            Total Records: %d
            Matched: %d
            Exceptions: %d
            Total Credits: ₹%s
            Total Debits: ₹%s
            Net Balance: ₹%s
            Status: %s
            
            Exceptions found:
            %s
            
            Provide a 2-3 sentence executive summary explaining:
            1. Overall health of this batch
            2. Key concerns if any
            3. Recommended action
            
            Be concise and professional.
            """.formatted(batchId, total, matched, exceptions,
                credits, debits, net, status,
                exceptionText.length() > 0 ? exceptionText.toString() : "None");

        return geminiService.ask(prompt);
    }
}
