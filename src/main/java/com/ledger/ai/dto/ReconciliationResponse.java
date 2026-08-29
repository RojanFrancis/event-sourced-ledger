package com.ledger.ai.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ReconciliationResponse {

    private String batchId;
    private int totalRecords;
    private int matchedCount;
    private int exceptionCount;
    private String matchRate;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netBalance;
    private String status;           // BALANCED or UNBALANCED
    private List<ExceptionDetail> exceptions;
    private String aiSummary;        // AI generated explanation
    private String processedAt;

    @Data
    @Builder
    public static class ExceptionDetail {
        private String transactionId;
        private String accountId;
        private String type;
        private BigDecimal amount;
        private String reason;        // AI explains why this is flagged
        private String severity;      // HIGH, MEDIUM, LOW
    }
}
