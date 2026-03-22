package com.scopesky.paymentservice.dto.transfer;

import com.scopesky.paymentservice.model.enums.Currency;
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
public class TransferResponse {
    private Long id;
    private String referenceId;
    private String sourceWalletReferenceId;
    private String destinationWalletReferenceId;
    private BigDecimal amount;
    private Currency currency;
    private String debitTransactionReferenceId;
    private String creditTransactionReferenceId;
    private String description;
    private LocalDateTime createdAt;
}
