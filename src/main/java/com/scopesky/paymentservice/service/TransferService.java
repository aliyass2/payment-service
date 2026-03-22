package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.transfer.TransferRequest;
import com.scopesky.paymentservice.dto.transfer.TransferResponse;
import com.scopesky.paymentservice.exception.*;
import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.Transfer;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.TransactionStatus;
import com.scopesky.paymentservice.model.enums.TransactionType;
import com.scopesky.paymentservice.model.enums.WalletStatus;
import com.scopesky.paymentservice.repository.TransactionRepository;
import com.scopesky.paymentservice.repository.TransferRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        if (request.getSourceWalletId().equals(request.getDestinationWalletId())) {
            throw new InvalidOperationException("Cannot transfer to the same wallet");
        }

        transferRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(existing -> {
                    throw new DuplicateIdempotencyKeyException(request.getIdempotencyKey());
                });

        Wallet source = walletRepository.findById(request.getSourceWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getSourceWalletId()));
        Wallet destination = walletRepository.findById(request.getDestinationWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getDestinationWalletId()));

        if (source.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletFrozenException(source.getId());
        }
        if (destination.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletFrozenException(destination.getId());
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw new WalletCurrencyMismatchException(source.getCurrency(), destination.getCurrency());
        }

        BigDecimal amount = request.getAmount();
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(source.getBalance(), amount);
        }

        BigDecimal srcBefore = source.getBalance();
        source.setBalance(srcBefore.subtract(amount));
        walletRepository.save(source);

        Transaction debitTxn = Transaction.builder()
                .wallet(source)
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .currency(source.getCurrency())
                .balanceBefore(srcBefore)
                .balanceAfter(source.getBalance())
                .description(request.getDescription())
                .build();
        debitTxn = transactionRepository.save(debitTxn);

        BigDecimal dstBefore = destination.getBalance();
        destination.setBalance(dstBefore.add(amount));
        walletRepository.save(destination);

        Transaction creditTxn = Transaction.builder()
                .wallet(destination)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .currency(destination.getCurrency())
                .balanceBefore(dstBefore)
                .balanceAfter(destination.getBalance())
                .description(request.getDescription())
                .relatedTransactionId(debitTxn.getId())
                .build();
        creditTxn = transactionRepository.save(creditTxn);

        Transfer transfer = Transfer.builder()
                .sourceWallet(source)
                .destinationWallet(destination)
                .amount(amount)
                .currency(source.getCurrency())
                .debitTransaction(debitTxn)
                .creditTransaction(creditTxn)
                .description(request.getDescription())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        return toResponse(transferRepository.save(transfer));
    }

    public TransferResponse getTransferById(Long id) {
        return toResponse(transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id)));
    }

    public TransferResponse getTransferByReferenceId(String referenceId) {
        return toResponse(transferRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new TransferNotFoundException(referenceId)));
    }

    public List<TransferResponse> getAllTransfers() {
        return transferRepository.findAll().stream().map(this::toResponse).toList();
    }

    private TransferResponse toResponse(Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .referenceId(transfer.getReferenceId())
                .sourceWalletReferenceId(transfer.getSourceWallet().getReferenceId())
                .destinationWalletReferenceId(transfer.getDestinationWallet().getReferenceId())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .debitTransactionReferenceId(transfer.getDebitTransaction().getReferenceId())
                .creditTransactionReferenceId(transfer.getCreditTransaction().getReferenceId())
                .description(transfer.getDescription())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
}
