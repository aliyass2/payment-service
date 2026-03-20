package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.PaymentRequest;
import com.scopesky.paymentservice.dto.PaymentResponse;
import com.scopesky.paymentservice.exception.PaymentNotFoundException;
import com.scopesky.paymentservice.model.Payment;
import com.scopesky.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public PaymentResponse getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public PaymentResponse createPayment(PaymentRequest request) {
        return toResponse(paymentRepository.save(toEntity(request)));
    }

    public PaymentResponse updatePayment(Long id, PaymentRequest request) {
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        existing.setAmount(request.getAmount());
        existing.setStatus(request.getStatus());
        return toResponse(paymentRepository.save(existing));
    }

    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new PaymentNotFoundException(id);
        }
        paymentRepository.deleteById(id);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }

    private Payment toEntity(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setStatus(request.getStatus());
        return payment;
    }
}
