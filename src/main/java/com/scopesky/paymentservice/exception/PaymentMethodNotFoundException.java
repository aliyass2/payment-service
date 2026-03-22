package com.scopesky.paymentservice.exception;

public class PaymentMethodNotFoundException extends RuntimeException {
    public PaymentMethodNotFoundException(Long id) {
        super("Payment method not found with id: " + id);
    }
}
