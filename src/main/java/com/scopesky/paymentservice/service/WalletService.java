package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.transaction.TransactionResponse;
import com.scopesky.paymentservice.dto.wallet.*;
import com.scopesky.paymentservice.exception.*;
import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.TransactionStatus;
import com.scopesky.paymentservice.model.enums.TransactionType;
import com.scopesky.paymentservice.model.enums.UserStatus;
import com.scopesky.paymentservice.model.enums.WalletStatus;
import com.scopesky.paymentservice.repository.TransactionRepository;
import com.scopesky.paymentservice.repository.UserRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot add wallet to non-active user");
        }
        Wallet wallet = Wallet.builder()
                .user(user)
                .currency(request.getCurrency())
                .label(request.getLabel())
                .balance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
        return toResponse(walletRepository.save(wallet));
    }

    public WalletResponse getWalletById(Long id) {
        return toResponse(findById(id));
    }

    public WalletResponse getWalletByReferenceId(String referenceId) {
        return toResponse(walletRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new WalletNotFoundException(referenceId)));
    }

    public List<WalletResponse> getWalletsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return walletRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TransactionResponse deposit(Long walletId, DepositRequest request) {
        Wallet wallet = findById(walletId);
        checkIdempotency(request.getIdempotencyKey());
        assertWalletActive(wallet);

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(balanceBefore.add(request.getAmount()));
        walletRepository.save(wallet);

        Transaction txn = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        return toTransactionResponse(transactionRepository.save(txn));
    }

    @Transactional
    public TransactionResponse withdraw(Long walletId, WithdrawRequest request) {
        Wallet wallet = findById(walletId);
        checkIdempotency(request.getIdempotencyKey());
        assertWalletActive(wallet);
        assertSufficientFunds(wallet, request.getAmount());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(balanceBefore.subtract(request.getAmount()));
        walletRepository.save(wallet);

        Transaction txn = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.COMPLETED)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        return toTransactionResponse(transactionRepository.save(txn));
    }

    @Transactional
    public TransactionResponse pay(Long walletId, PayRequest request) {
        Wallet wallet = findById(walletId);
        checkIdempotency(request.getIdempotencyKey());
        assertWalletActive(wallet);
        assertSufficientFunds(wallet, request.getAmount());

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(balanceBefore.subtract(request.getAmount()));
        walletRepository.save(wallet);

        String desc = request.getMerchantReference()
                + (request.getDescription() != null ? " - " + request.getDescription() : "");
        Transaction txn = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(desc)
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        return toTransactionResponse(transactionRepository.save(txn));
    }

    @Transactional
    public TransactionResponse refund(Long walletId, RefundRequest request) {
        Wallet wallet = findById(walletId);
        checkIdempotency(request.getIdempotencyKey());
        assertWalletActive(wallet);

        Transaction original = transactionRepository.findById(request.getOriginalTransactionId())
                .orElseThrow(() -> new TransactionNotFoundException(request.getOriginalTransactionId()));
        if (original.getType() != TransactionType.PAYMENT) {
            throw new InvalidOperationException("Can only refund PAYMENT transactions");
        }
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new InvalidOperationException("Can only refund COMPLETED transactions");
        }
        if (request.getAmount().compareTo(original.getAmount()) > 0) {
            throw new InvalidOperationException("Refund amount exceeds original payment amount");
        }

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(balanceBefore.add(request.getAmount()));
        walletRepository.save(wallet);

        Transaction txn = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.REFUND)
                .status(TransactionStatus.COMPLETED)
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(request.getReason())
                .relatedTransactionId(original.getId())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        return toTransactionResponse(transactionRepository.save(txn));
    }

    @Transactional
    public WalletResponse freezeWallet(Long id) {
        Wallet wallet = findById(id);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new InvalidOperationException("Only ACTIVE wallets can be frozen");
        }
        wallet.setStatus(WalletStatus.FROZEN);
        return toResponse(walletRepository.save(wallet));
    }

    @Transactional
    public WalletResponse unfreezeWallet(Long id) {
        Wallet wallet = findById(id);
        if (wallet.getStatus() != WalletStatus.FROZEN) {
            throw new InvalidOperationException("Only FROZEN wallets can be unfrozen");
        }
        wallet.setStatus(WalletStatus.ACTIVE);
        return toResponse(walletRepository.save(wallet));
    }

    private Wallet findById(Long id) {
        return walletRepository.findById(id).orElseThrow(() -> new WalletNotFoundException(id));
    }

    private void checkIdempotency(String key) {
        transactionRepository.findByIdempotencyKey(key).ifPresent(existing -> {
            throw new DuplicateIdempotencyKeyException(key);
        });
    }

    private void assertWalletActive(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletFrozenException(wallet.getId());
        }
    }

    private void assertSufficientFunds(Wallet wallet, BigDecimal amount) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(wallet.getBalance(), amount);
        }
    }

    WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .referenceId(wallet.getReferenceId())
                .userId(wallet.getUser().getId())
                .userReferenceId(wallet.getUser().getReferenceId())
                .currency(wallet.getCurrency())
                .balance(wallet.getBalance())
                .label(wallet.getLabel())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    TransactionResponse toTransactionResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .referenceId(txn.getReferenceId())
                .walletId(txn.getWallet().getId())
                .walletReferenceId(txn.getWallet().getReferenceId())
                .type(txn.getType())
                .status(txn.getStatus())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .balanceBefore(txn.getBalanceBefore())
                .balanceAfter(txn.getBalanceAfter())
                .description(txn.getDescription())
                .relatedTransactionId(txn.getRelatedTransactionId())
                .idempotencyKey(txn.getIdempotencyKey())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
