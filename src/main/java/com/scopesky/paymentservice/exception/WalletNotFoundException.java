package com.scopesky.paymentservice.exception;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(Long id) {
        super("Wallet not found with id: " + id);
    }

    public WalletNotFoundException(String referenceId) {
        super("Wallet not found with referenceId: " + referenceId);
    }
}
