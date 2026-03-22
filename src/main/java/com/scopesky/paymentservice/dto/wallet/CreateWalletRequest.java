package com.scopesky.paymentservice.dto.wallet;

import com.scopesky.paymentservice.model.enums.Currency;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Currency is required")
    private Currency currency;

    private String label;
}
