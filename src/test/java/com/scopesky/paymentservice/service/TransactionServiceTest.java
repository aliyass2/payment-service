package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.transaction.TransactionResponse;
import com.scopesky.paymentservice.exception.InvalidOperationException;
import com.scopesky.paymentservice.exception.TransactionNotFoundException;
import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.*;
import com.scopesky.paymentservice.repository.TransactionRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletService walletService;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private Wallet activeWallet;
    private Transaction completedPayment;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).referenceId("USR-TEST").status(UserStatus.ACTIVE).build();
        activeWallet = Wallet.builder()
                .id(10L).referenceId("WLT-TEST")
                .user(user).currency(Currency.USD)
                .balance(new BigDecimal("100.00"))
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        completedPayment = Transaction.builder()
                .id(1L).referenceId("TXN-PAYMENT1")
                .wallet(activeWallet)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .currency(Currency.USD)
                .balanceBefore(new BigDecimal("150.00"))
                .balanceAfter(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void getTransactionById_nonExistingId_throwsTransactionNotFoundException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionById(99L))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void reverseTransaction_completedPayment_creditsWalletAndCreatesReversal() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(completedPayment));
        when(walletRepository.save(any())).thenReturn(activeWallet);
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getType() == TransactionType.REVERSAL) {
                t = Transaction.builder()
                        .id(2L).referenceId("TXN-REVERSAL")
                        .wallet(activeWallet)
                        .type(TransactionType.REVERSAL)
                        .status(TransactionStatus.COMPLETED)
                        .amount(new BigDecimal("50.00"))
                        .currency(Currency.USD)
                        .balanceBefore(new BigDecimal("100.00"))
                        .balanceAfter(new BigDecimal("150.00"))
                        .relatedTransactionId(1L)
                        .createdAt(LocalDateTime.now()).build();
            }
            return t;
        });
        when(walletService.toTransactionResponse(any())).thenCallRealMethod();

        transactionService.reverseTransaction(1L);

        assertThat(activeWallet.getBalance()).isEqualByComparingTo("150.00");
        assertThat(completedPayment.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void reverseTransaction_alreadyReversed_throwsInvalidOperationException() {
        completedPayment.setStatus(TransactionStatus.REVERSED);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        assertThatThrownBy(() -> transactionService.reverseTransaction(1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void reverseTransaction_reversalType_throwsInvalidOperationException() {
        completedPayment.setType(TransactionType.REVERSAL);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        assertThatThrownBy(() -> transactionService.reverseTransaction(1L))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void reverseTransaction_nonExistingId_throwsTransactionNotFoundException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.reverseTransaction(99L))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
