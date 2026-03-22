package com.scopesky.paymentservice.dto.wallet;

import com.scopesky.paymentservice.model.enums.Currency;
import com.scopesky.paymentservice.model.enums.WalletStatus;
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
public class WalletResponse {
    private Long id;
    private String referenceId;
    private Long userId;
    private String userReferenceId;
    private Currency currency;
    private BigDecimal balance;
    private String label;
    private WalletStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
