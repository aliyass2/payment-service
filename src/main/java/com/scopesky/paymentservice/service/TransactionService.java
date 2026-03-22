package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.transaction.TransactionResponse;
import com.scopesky.paymentservice.exception.InvalidOperationException;
import com.scopesky.paymentservice.exception.InsufficientFundsException;
import com.scopesky.paymentservice.exception.TransactionNotFoundException;
import com.scopesky.paymentservice.exception.WalletFrozenException;
import com.scopesky.paymentservice.model.Transaction;
import com.scopesky.paymentservice.model.Wallet;
import com.scopesky.paymentservice.model.enums.TransactionStatus;
import com.scopesky.paymentservice.model.enums.TransactionType;
import com.scopesky.paymentservice.model.enums.WalletStatus;
import com.scopesky.paymentservice.repository.TransactionRepository;
import com.scopesky.paymentservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;

    public TransactionResponse getTransactionById(Long id) {
        return walletService.toTransactionResponse(findById(id));
    }

    public TransactionResponse getTransactionByReferenceId(String referenceId) {
        return walletService.toTransactionResponse(
                transactionRepository.findByReferenceId(referenceId)
                        .orElseThrow(() -> new TransactionNotFoundException(referenceId)));
    }

    public List<TransactionResponse> getTransactionsByWallet(Long walletId, TransactionType type) {
        List<Transaction> transactions = type != null
                ? transactionRepository.findByWalletIdAndTypeOrderByCreatedAtDesc(walletId, type)
                : transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
        return transactions.stream().map(walletService::toTransactionResponse).toList();
    }

    @Transactional
    public TransactionResponse reverseTransaction(Long id) {
        Transaction original = findById(id);
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new InvalidOperationException("Only COMPLETED transactions can be reversed");
        }
        if (original.getType() == TransactionType.REVERSAL) {
            throw new InvalidOperationException("A reversal cannot be reversed");
        }

        Wallet wallet = original.getWallet();
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletFrozenException(wallet.getId());
        }

        BigDecimal amount = original.getAmount();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter;

        boolean isDebit = original.getType() == TransactionType.PAYMENT
                || original.getType() == TransactionType.WITHDRAWAL
                || original.getType() == TransactionType.TRANSFER_OUT;

        if (isDebit) {
            balanceAfter = balanceBefore.add(amount);
        } else {
            if (balanceBefore.compareTo(amount) < 0) {
                throw new InsufficientFundsException(balanceBefore, amount);
            }
            balanceAfter = balanceBefore.subtract(amount);
        }

        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        Transaction reversal = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.REVERSAL)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .currency(original.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description("Reversal of " + original.getReferenceId())
                .relatedTransactionId(original.getId())
                .build();
        return walletService.toTransactionResponse(transactionRepository.save(reversal));
    }

    private Transaction findById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
