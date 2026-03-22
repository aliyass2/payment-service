package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.user.CreateUserRequest;
import com.scopesky.paymentservice.dto.user.UserResponse;
import com.scopesky.paymentservice.exception.InvalidOperationException;
import com.scopesky.paymentservice.exception.UserNotFoundException;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.Currency;
import com.scopesky.paymentservice.model.enums.UserStatus;
import com.scopesky.paymentservice.model.enums.WalletStatus;
import com.scopesky.paymentservice.repository.UserRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidOperationException("Email already registered: " + request.getEmail());
        }
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        Wallet defaultWallet = Wallet.builder()
                .user(user)
                .currency(Currency.USD)
                .label("Default")
                .balance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(defaultWallet);

        return toResponse(user);
    }

    public UserResponse getUserById(Long id) {
        return toResponse(findById(id));
    }

    public UserResponse getUserByReferenceId(String referenceId) {
        return toResponse(userRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new UserNotFoundException(referenceId)));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserResponse suspendUser(Long id) {
        User user = findById(id);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidOperationException("User is not active and cannot be suspended");
        }
        user.setStatus(UserStatus.SUSPENDED);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse activateUser(Long id) {
        User user = findById(id);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new InvalidOperationException("User is not suspended and cannot be activated");
        }
        user.setStatus(UserStatus.ACTIVE);
        return toResponse(userRepository.save(user));
    }

    private User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .referenceId(user.getReferenceId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
