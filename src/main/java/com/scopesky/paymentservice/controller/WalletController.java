package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.dto.transaction.TransactionResponse;
import com.scopesky.paymentservice.dto.wallet.*;
import com.scopesky.paymentservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/api/wallets")
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(walletService.createWallet(request));
    }

    @GetMapping("/api/wallets/{id}")
    public ResponseEntity<WalletResponse> getWalletById(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.getWalletById(id));
    }

    @GetMapping("/api/wallets/ref/{referenceId}")
    public ResponseEntity<WalletResponse> getWalletByReferenceId(@PathVariable String referenceId) {
        return ResponseEntity.ok(walletService.getWalletByReferenceId(referenceId));
    }

    @GetMapping("/api/users/{userId}/wallets")
    public ResponseEntity<List<WalletResponse>> getWalletsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.getWalletsByUser(userId));
    }

    @PostMapping("/api/wallets/{id}/deposit")
    public ResponseEntity<TransactionResponse> deposit(@PathVariable Long id,
                                                       @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(walletService.deposit(id, request));
    }

    @PostMapping("/api/wallets/{id}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@PathVariable Long id,
                                                        @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(walletService.withdraw(id, request));
    }

    @PostMapping("/api/wallets/{id}/pay")
    public ResponseEntity<TransactionResponse> pay(@PathVariable Long id,
                                                   @Valid @RequestBody PayRequest request) {
        return ResponseEntity.ok(walletService.pay(id, request));
    }

    @PostMapping("/api/wallets/{id}/refund")
    public ResponseEntity<TransactionResponse> refund(@PathVariable Long id,
                                                      @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(walletService.refund(id, request));
    }

    @PutMapping("/api/wallets/{id}/freeze")
    public ResponseEntity<WalletResponse> freezeWallet(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.freezeWallet(id));
    }

    @PutMapping("/api/wallets/{id}/unfreeze")
    public ResponseEntity<WalletResponse> unfreezeWallet(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.unfreezeWallet(id));
    }
}
