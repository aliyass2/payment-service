package com.scopesky.paymentservice.repository;

import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByReferenceId(String referenceId);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);
    List<Transaction> findByWalletIdAndTypeOrderByCreatedAtDesc(Long walletId, TransactionType type);
}
