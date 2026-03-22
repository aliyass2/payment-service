package com.scopesky.paymentservice.repository;

import com.scopesky.paymentservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByReferenceId(String referenceId);
    boolean existsByEmail(String email);
}
