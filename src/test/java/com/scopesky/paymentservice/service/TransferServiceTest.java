package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.transfer.TransferRequest;
import com.scopesky.paymentservice.dto.transfer.TransferResponse;
import com.scopesky.paymentservice.exception.*;
import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.Transfer;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.*;
import com.scopesky.paymentservice.repository.TransactionRepository;
import com.scopesky.paymentservice.repository.TransferRepository;
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
class TransferServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransferRepository transferRepository;

    @InjectMocks
    private TransferService transferService;

    private User userA;
    private User userB;
    private Wallet walletA;
    private Wallet walletB;

    @BeforeEach
    void setUp() {
        userA = User.builder().id(1L).referenceId("USR-AAAA").status(UserStatus.ACTIVE).build();
        userB = User.builder().id(2L).referenceId("USR-BBBB").status(UserStatus.ACTIVE).build();
        walletA = Wallet.builder().id(10L).referenceId("WLT-AAAA")
                .user(userA).currency(Currency.USD)
                .balance(new BigDecimal("500.00")).status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        walletB = Wallet.builder().id(20L).referenceId("WLT-BBBB")
                .user(userB).currency(Currency.USD)
                .balance(BigDecimal.ZERO).status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private Transaction stubTxn(Long id, String ref, TransactionType type) {
        return Transaction.builder()
                .id(id).referenceId(ref).wallet(walletA)
                .type(type).status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("100.00")).currency(Currency.USD)
                .balanceBefore(BigDecimal.ZERO).balanceAfter(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void transfer_validRequest_createsTwoTransactionsAndTransferRecord() {
        when(transferRepository.findByIdempotencyKey("trf-key-1")).thenReturn(Optional.empty());
        when(walletRepository.findById(10L)).thenReturn(Optional.of(walletA));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(walletB));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any()))
                .thenReturn(stubTxn(1L, "TXN-DEBIT001", TransactionType.TRANSFER_OUT))
                .thenReturn(stubTxn(2L, "TXN-CREDIT01", TransactionType.TRANSFER_IN));
        Transfer savedTransfer = Transfer.builder()
                .id(1L).referenceId("TRF-TEST001")
                .sourceWallet(walletA).destinationWallet(walletB)
                .amount(new BigDecimal("100.00")).currency(Currency.USD)
                .debitTransaction(stubTxn(1L, "TXN-DEBIT001", TransactionType.TRANSFER_OUT))
                .creditTransaction(stubTxn(2L, "TXN-CREDIT01", TransactionType.TRANSFER_IN))
                .createdAt(LocalDateTime.now()).build();
        when(transferRepository.save(any())).thenReturn(savedTransfer);

        TransferResponse response = transferService.transfer(
                new TransferRequest(10L, 20L, new BigDecimal("100.00"), "Test transfer", "trf-key-1"));

        assertThat(response.getReferenceId()).isEqualTo("TRF-TEST001");
        verify(transactionRepository, times(2)).save(any());
        verify(transferRepository).save(any());
    }

    @Test
    void transfer_sameWalletId_throwsInvalidOperationException() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(10L, 10L, new BigDecimal("100.00"), null, "trf-key-2")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("same wallet");
    }

    @Test
    void transfer_sourceInsufficientFunds_throwsInsufficientFundsException() {
        when(transferRepository.findByIdempotencyKey("trf-key-3")).thenReturn(Optional.empty());
        when(walletRepository.findById(10L)).thenReturn(Optional.of(walletA));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(walletB));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(10L, 20L, new BigDecimal("9999.00"), null, "trf-key-3")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void transfer_currencyMismatch_throwsWalletCurrencyMismatchException() {
        walletB.setCurrency(Currency.EUR);
        when(transferRepository.findByIdempotencyKey("trf-key-4")).thenReturn(Optional.empty());
        when(walletRepository.findById(10L)).thenReturn(Optional.of(walletA));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(walletB));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(10L, 20L, new BigDecimal("100.00"), null, "trf-key-4")))
                .isInstanceOf(WalletCurrencyMismatchException.class);
    }

    @Test
    void transfer_sourceWalletFrozen_throwsWalletFrozenException() {
        walletA.setStatus(WalletStatus.FROZEN);
        when(transferRepository.findByIdempotencyKey("trf-key-5")).thenReturn(Optional.empty());
        when(walletRepository.findById(10L)).thenReturn(Optional.of(walletA));
        when(walletRepository.findById(20L)).thenReturn(Optional.of(walletB));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(10L, 20L, new BigDecimal("100.00"), null, "trf-key-5")))
                .isInstanceOf(WalletFrozenException.class);
    }
}
