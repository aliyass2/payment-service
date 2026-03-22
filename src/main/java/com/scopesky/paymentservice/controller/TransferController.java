package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.dto.transfer.TransferRequest;
import com.scopesky.paymentservice.dto.transfer.TransferResponse;
import com.scopesky.paymentservice.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.transfer(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransferById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getTransferById(id));
    }

    @GetMapping("/ref/{referenceId}")
    public ResponseEntity<TransferResponse> getTransferByReferenceId(@PathVariable String referenceId) {
        return ResponseEntity.ok(transferService.getTransferByReferenceId(referenceId));
    }
}
