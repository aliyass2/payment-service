package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.dto.transaction.TransactionResponse;
import com.scopesky.paymentservice.model.enums.TransactionType;
import com.scopesky.paymentservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/api/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @GetMapping("/api/transactions/ref/{referenceId}")
    public ResponseEntity<TransactionResponse> getTransactionByReferenceId(@PathVariable String referenceId) {
        return ResponseEntity.ok(transactionService.getTransactionByReferenceId(referenceId));
    }

    @GetMapping("/api/wallets/{walletId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByWallet(
            @PathVariable Long walletId,
            @RequestParam(required = false) TransactionType type) {
        return ResponseEntity.ok(transactionService.getTransactionsByWallet(walletId, type));
    }

    @PostMapping("/api/transactions/{id}/reverse")
    public ResponseEntity<TransactionResponse> reverseTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.reverseTransaction(id));
    }
}
