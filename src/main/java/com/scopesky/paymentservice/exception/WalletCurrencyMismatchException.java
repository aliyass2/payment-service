package com.scopesky.paymentservice.exception;

import com.scopesky.paymentservice.model.enums.Currency;

public class WalletCurrencyMismatchException extends RuntimeException {
    public WalletCurrencyMismatchException(Currency src, Currency dst) {
        super("Currency mismatch: source wallet is " + src + ", destination is " + dst);
    }
}
