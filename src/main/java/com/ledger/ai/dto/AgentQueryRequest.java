package com.ledger.ai.dto;

import lombok.Data;

@Data
public class AgentQueryRequest {
    private String accountId;
    private String question;
}
