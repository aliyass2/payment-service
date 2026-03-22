package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.dto.paymentmethod.AddPaymentMethodRequest;
import com.scopesky.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import com.scopesky.paymentservice.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @PostMapping("/api/payment-methods")
    public ResponseEntity<PaymentMethodResponse> addPaymentMethod(
            @Valid @RequestBody AddPaymentMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMethodService.addPaymentMethod(request));
    }

    @GetMapping("/api/payment-methods/{id}")
    public ResponseEntity<PaymentMethodResponse> getPaymentMethodById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentMethodService.getPaymentMethodById(id));
    }

    @GetMapping("/api/users/{userId}/payment-methods")
    public ResponseEntity<List<PaymentMethodResponse>> getPaymentMethodsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentMethodService.getPaymentMethodsByUser(userId));
    }

    @PutMapping("/api/payment-methods/{id}/set-default")
    public ResponseEntity<PaymentMethodResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(paymentMethodService.setDefault(id));
    }

    @DeleteMapping("/api/payment-methods/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        paymentMethodService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
