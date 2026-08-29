package com.ledger.ai.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReconciliationRequest {

    private String batchId;
    private List<TransactionRecord> records;

    @Data
    public static class TransactionRecord {
        private String transactionId;
        private String type;        // CREDIT or DEBIT
        private BigDecimal amount;
        private String accountId;
        private String description;
        private String timestamp;
    }
}
