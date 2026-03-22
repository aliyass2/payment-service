package com.scopesky.paymentservice.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }

    public UserNotFoundException(String referenceId) {
        super("User not found with referenceId: " + referenceId);
    }
}
