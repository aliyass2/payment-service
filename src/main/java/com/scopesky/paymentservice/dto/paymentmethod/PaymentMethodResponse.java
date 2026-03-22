package com.scopesky.paymentservice.dto.paymentmethod;

import com.scopesky.paymentservice.model.enums.PaymentMethodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {
    private Long id;
    private String referenceId;
    private Long userId;
    private PaymentMethodType type;
    private String provider;
    private String maskedIdentifier;
    private String expiryDate;
    private boolean isDefault;
    private boolean active;
    private LocalDateTime createdAt;
}
