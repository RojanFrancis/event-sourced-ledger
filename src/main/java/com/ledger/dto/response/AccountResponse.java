package com.ledger.dto.response;

import com.ledger.enums.AccountStatus;
import com.ledger.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID id;
    private String ownerName;
    private AccountType accountType;
    private String currency;
    private AccountStatus status;
    private BigDecimal currentBalance;
    private LocalDateTime createdAt;
}
