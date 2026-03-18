package com.scopesky.paymentservice.repository;

import com.scopesky.paymentservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // we extended the original JPARepostiory here, which provide me with full Crud
    // Operations
}