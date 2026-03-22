package com.scopesky.paymentservice.repository;

import com.scopesky.paymentservice.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByReferenceId(String referenceId);
    List<Wallet> findByUserId(Long userId);
}
