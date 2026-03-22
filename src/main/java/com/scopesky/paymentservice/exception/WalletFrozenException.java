package com.scopesky.paymentservice.exception;

public class WalletFrozenException extends RuntimeException {
    public WalletFrozenException(Long walletId) {
        super("Wallet " + walletId + " is frozen and cannot process transactions");
    }
}
