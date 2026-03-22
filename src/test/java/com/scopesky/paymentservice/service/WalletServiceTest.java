package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.transaction.TransactionResponse;
import com.scopesky.paymentservice.dto.wallet.DepositRequest;
import com.scopesky.paymentservice.dto.wallet.PayRequest;
import com.scopesky.paymentservice.dto.wallet.RefundRequest;
import com.scopesky.paymentservice.dto.wallet.WithdrawRequest;
import com.scopesky.paymentservice.exception.*;
import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.*;
import com.scopesky.paymentservice.repository.TransactionRepository;
import com.scopesky.paymentservice.repository.UserRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletService walletService;

    private User user;
    private Wallet activeWallet;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).referenceId("USR-TEST001").status(UserStatus.ACTIVE).build();
        activeWallet = Wallet.builder()
                .id(10L)
                .referenceId("WLT-TEST001")
                .user(user)
                .currency(Currency.USD)
                .balance(new BigDecimal("200.00"))
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Transaction mockSavedTxn(TransactionType type) {
        return Transaction.builder()
                .id(100L)
                .referenceId("TXN-ABCDEF12")
                .wallet(activeWallet)
                .type(type)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.USD)
                .balanceBefore(new BigDecimal("200.00"))
                .balanceAfter(new BigDecimal("250.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void deposit_validRequest_updatesBalanceAndCreatesTransaction() {
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenReturn(activeWallet);
        when(transactionRepository.save(any())).thenReturn(mockSavedTxn(TransactionType.DEPOSIT));

        TransactionResponse response = walletService.deposit(10L,
                new DepositRequest(new BigDecimal("50.00"), "Test", "key-1"));

        assertThat(response.getType()).isEqualTo(TransactionType.DEPOSIT);
        verify(walletRepository).save(activeWallet);
        verify(transactionRepository).save(any());
    }

    @Test
    void deposit_frozenWallet_throwsWalletFrozenException() {
        activeWallet.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deposit(10L,
                new DepositRequest(new BigDecimal("50.00"), null, "key-1")))
                .isInstanceOf(WalletFrozenException.class);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void deposit_duplicateIdempotencyKey_throwsDuplicateIdempotencyKeyException() {
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("dup-key"))
                .thenReturn(Optional.of(mockSavedTxn(TransactionType.DEPOSIT)));

        assertThatThrownBy(() -> walletService.deposit(10L,
                new DepositRequest(new BigDecimal("50.00"), null, "dup-key")))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    @Test
    void withdraw_sufficientFunds_updatesBalance() {
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-w1")).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenReturn(activeWallet);
        when(transactionRepository.save(any())).thenReturn(mockSavedTxn(TransactionType.WITHDRAWAL));

        TransactionResponse response = walletService.withdraw(10L,
                new WithdrawRequest(new BigDecimal("50.00"), null, "key-w1"));

        assertThat(response.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        verify(walletRepository).save(activeWallet);
    }

    @Test
    void withdraw_insufficientFunds_throwsInsufficientFundsException() {
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-w2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.withdraw(10L,
                new WithdrawRequest(new BigDecimal("9999.00"), null, "key-w2")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient");

        verify(walletRepository, never()).save(any());
    }

    @Test
    void pay_sufficientBalance_createsPaymentTransaction() {
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-p1")).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenReturn(activeWallet);
        when(transactionRepository.save(any())).thenReturn(mockSavedTxn(TransactionType.PAYMENT));

        TransactionResponse response = walletService.pay(10L,
                new PayRequest(new BigDecimal("50.00"), "MERCHANT-1", null, "key-p1"));

        assertThat(response.getType()).isEqualTo(TransactionType.PAYMENT);
    }

    @Test
    void refund_validPayment_creditsWallet() {
        Transaction originalPayment = Transaction.builder()
                .id(50L)
                .wallet(activeWallet)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .balanceBefore(new BigDecimal("200.00"))
                .balanceAfter(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now())
                .build();

        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-r1")).thenReturn(Optional.empty());
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(originalPayment));
        when(walletRepository.save(any())).thenReturn(activeWallet);
        when(transactionRepository.save(any())).thenReturn(mockSavedTxn(TransactionType.REFUND));

        TransactionResponse response = walletService.refund(10L,
                new RefundRequest(50L, new BigDecimal("50.00"), "Partial refund", "key-r1"));

        assertThat(response.getType()).isEqualTo(TransactionType.REFUND);
        verify(walletRepository).save(any());
    }

    @Test
    void refund_amountExceedsOriginal_throwsInvalidOperationException() {
        Transaction originalPayment = Transaction.builder()
                .id(50L)
                .wallet(activeWallet)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("30.00"))
                .currency(Currency.USD)
                .balanceBefore(BigDecimal.ZERO)
                .balanceAfter(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-r2")).thenReturn(Optional.empty());
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(originalPayment));

        assertThatThrownBy(() -> walletService.refund(10L,
                new RefundRequest(50L, new BigDecimal("100.00"), null, "key-r2")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void refund_nonPaymentTransaction_throwsInvalidOperationException() {
        Transaction deposit = Transaction.builder()
                .id(50L)
                .wallet(activeWallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .balanceBefore(BigDecimal.ZERO)
                .balanceAfter(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now())
                .build();

        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(transactionRepository.findByIdempotencyKey("key-r3")).thenReturn(Optional.empty());
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> walletService.refund(10L,
                new RefundRequest(50L, new BigDecimal("10.00"), null, "key-r3")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("PAYMENT");
    }

    @Test
    void freezeWallet_activeWallet_updatesStatusToFrozen() {
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.freezeWallet(10L);

        assertThat(activeWallet.getStatus()).isEqualTo(WalletStatus.FROZEN);
    }

    @Test
    void freezeWallet_alreadyFrozen_throwsInvalidOperationException() {
        activeWallet.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));

        assertThatThrownBy(() -> walletService.freezeWallet(10L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void unfreezeWallet_frozenWallet_updatesStatusToActive() {
        activeWallet.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findById(10L)).thenReturn(Optional.of(activeWallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.unfreezeWallet(10L);

        assertThat(activeWallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }
}
