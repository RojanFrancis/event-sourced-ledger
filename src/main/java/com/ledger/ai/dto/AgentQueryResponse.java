package com.ledger.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentQueryResponse {
    private String accountId;
    private String question;
    private String answer;
    private String reasoning;
    private String dataUsed;
}
