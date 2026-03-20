package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.PaymentRequest;
import com.scopesky.paymentservice.dto.PaymentResponse;
import com.scopesky.paymentservice.exception.PaymentNotFoundException;
import com.scopesky.paymentservice.model.Payment;
import com.scopesky.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment(1L, new BigDecimal("100.00"), "PENDING", LocalDateTime.now());
    }

    // -----------------------------------------------------------------------
    // getAllPayments
    // -----------------------------------------------------------------------

    @Test
    void getAllPayments_noPayments_returnsEmptyList() {
        when(paymentRepository.findAll()).thenReturn(List.of());

        assertThat(paymentService.getAllPayments()).isEmpty();
    }

    @Test
    void getAllPayments_withPayments_returnsMappedResponses() {
        when(paymentRepository.findAll()).thenReturn(List.of(payment));

        List<PaymentResponse> result = paymentService.getAllPayments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    // -----------------------------------------------------------------------
    // getPaymentById
    // -----------------------------------------------------------------------

    @Test
    void getPaymentById_existingId_returnsResponse() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPaymentById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void getPaymentById_nonExistingId_throwsPaymentNotFoundException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -----------------------------------------------------------------------
    // createPayment
    // -----------------------------------------------------------------------

    @Test
    void createPayment_validRequest_savesAndReturnsResponse() {
        PaymentRequest request = new PaymentRequest(new BigDecimal("200.00"), "COMPLETED");
        Payment saved = new Payment(2L, new BigDecimal("200.00"), "COMPLETED", LocalDateTime.now());
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

        PaymentResponse result = paymentService.createPayment(request);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getAmount()).isEqualByComparingTo("200.00");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(paymentRepository).save(any(Payment.class));
    }

    // -----------------------------------------------------------------------
    // updatePayment
    // -----------------------------------------------------------------------

    @Test
    void updatePayment_existingId_updatesAmountAndStatus() {
        PaymentRequest request = new PaymentRequest(new BigDecimal("300.00"), "REFUNDED");
        Payment updated = new Payment(1L, new BigDecimal("300.00"), "REFUNDED", payment.getCreatedAt());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(updated);

        PaymentResponse result = paymentService.updatePayment(1L, request);

        assertThat(result.getAmount()).isEqualByComparingTo("300.00");
        assertThat(result.getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void updatePayment_nonExistingId_throwsPaymentNotFoundException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.updatePayment(99L, new PaymentRequest(BigDecimal.TEN, "PENDING")))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("99");

        verify(paymentRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // deletePayment
    // -----------------------------------------------------------------------

    @Test
    void deletePayment_existingId_deletesSuccessfully() {
        when(paymentRepository.existsById(1L)).thenReturn(true);

        paymentService.deletePayment(1L);

        verify(paymentRepository).deleteById(1L);
    }

    @Test
    void deletePayment_nonExistingId_throwsPaymentNotFoundException() {
        when(paymentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.deletePayment(99L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("99");

        verify(paymentRepository, never()).deleteById(any());
    }
}
