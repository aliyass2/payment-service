package com.scopesky.paymentservice.dto.transaction;

import com.scopesky.paymentservice.model.enums.Currency;
import com.scopesky.paymentservice.model.enums.TransactionStatus;
import com.scopesky.paymentservice.model.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private String referenceId;
    private Long walletId;
    private String walletReferenceId;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private Currency currency;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String description;
    private Long relatedTransactionId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
