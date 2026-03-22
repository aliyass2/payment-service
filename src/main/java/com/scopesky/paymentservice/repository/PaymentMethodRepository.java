package com.scopesky.paymentservice.repository;

import com.scopesky.paymentservice.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserIdAndActiveTrue(Long userId);
    Optional<PaymentMethod> findByUserIdAndIsDefaultTrueAndActiveTrue(Long userId);
}
