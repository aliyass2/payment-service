package com.scopesky.paymentservice.repository;

import com.scopesky.paymentservice.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByReferenceId(String referenceId);
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
