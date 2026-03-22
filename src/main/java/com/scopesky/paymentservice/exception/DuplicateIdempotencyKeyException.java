package com.scopesky.paymentservice.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String key) {
        super("A transaction with idempotency key '" + key + "' already exists");
    }
}
