package com.scopesky.paymentservice.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer not found with id: " + id);
    }

    public TransferNotFoundException(String referenceId) {
        super("Transfer not found with referenceId: " + referenceId);
    }
}
