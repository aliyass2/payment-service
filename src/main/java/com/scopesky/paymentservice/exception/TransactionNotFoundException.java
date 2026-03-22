package com.scopesky.paymentservice.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(Long id) {
        super("Transaction not found with id: " + id);
    }

    public TransactionNotFoundException(String referenceId) {
        super("Transaction not found with referenceId: " + referenceId);
    }
}
