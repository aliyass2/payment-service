package com.scopesky.paymentservice.dto.paymentmethod;

import com.scopesky.paymentservice.model.enums.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddPaymentMethodRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType type;

    @NotBlank(message = "Provider is required")
    private String provider;

    @NotBlank(message = "Masked identifier is required")
    private String maskedIdentifier;

    private String expiryDate;

    private boolean isDefault;
}
