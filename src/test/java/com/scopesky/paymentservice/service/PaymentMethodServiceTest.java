package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.paymentmethod.AddPaymentMethodRequest;
import com.scopesky.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import com.scopesky.paymentservice.exception.PaymentMethodNotFoundException;
import com.scopesky.paymentservice.exception.UserNotFoundException;
import com.scopesky.paymentservice.model.PaymentMethod;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.enums.PaymentMethodType;
import com.scopesky.paymentservice.model.enums.UserStatus;
import com.scopesky.paymentservice.repository.PaymentMethodRepository;
import com.scopesky.paymentservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PaymentMethodService paymentMethodService;

    private User activeUser;
    private PaymentMethod existingMethod;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L).referenceId("USR-TEST")
                .status(UserStatus.ACTIVE).build();
        existingMethod = PaymentMethod.builder()
                .id(10L).referenceId("PMT-TEST01")
                .user(activeUser)
                .type(PaymentMethodType.CARD)
                .provider("Visa").maskedIdentifier("1234")
                .isDefault(true).active(true)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void addPaymentMethod_validRequest_savesAndReturnsResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(paymentMethodRepository.findByUserIdAndIsDefaultTrueAndActiveTrue(1L))
                .thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any())).thenReturn(existingMethod);

        PaymentMethodResponse response = paymentMethodService.addPaymentMethod(
                new AddPaymentMethodRequest(1L, PaymentMethodType.CARD, "Visa", "1234", "12/28", true));

        assertThat(response.getProvider()).isEqualTo("Visa");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void addPaymentMethod_isDefaultTrue_unsetsExistingDefault() {
        PaymentMethod previousDefault = PaymentMethod.builder()
                .id(5L).user(activeUser)
                .type(PaymentMethodType.CARD)
                .provider("Mastercard").maskedIdentifier("9999")
                .isDefault(true).active(true)
                .createdAt(LocalDateTime.now()).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(paymentMethodRepository.findByUserIdAndIsDefaultTrueAndActiveTrue(1L))
                .thenReturn(Optional.of(previousDefault));
        when(paymentMethodRepository.save(any())).thenReturn(existingMethod);

        paymentMethodService.addPaymentMethod(
                new AddPaymentMethodRequest(1L, PaymentMethodType.CARD, "Visa", "1234", null, true));

        assertThat(previousDefault.isDefault()).isFalse();
        verify(paymentMethodRepository, times(2)).save(any());
    }

    @Test
    void addPaymentMethod_nonExistingUser_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentMethodService.addPaymentMethod(
                new AddPaymentMethodRequest(99L, PaymentMethodType.CARD, "Visa", "1234", null, false)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void setDefault_validMethod_updatesDefaultFlag() {
        existingMethod.setDefault(false);
        when(paymentMethodRepository.findById(10L)).thenReturn(Optional.of(existingMethod));
        when(paymentMethodRepository.findByUserIdAndIsDefaultTrueAndActiveTrue(1L))
                .thenReturn(Optional.empty());
        when(paymentMethodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentMethodResponse response = paymentMethodService.setDefault(10L);

        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void setDefault_nonExistingId_throwsPaymentMethodNotFoundException() {
        when(paymentMethodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentMethodService.setDefault(99L))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void deactivate_existingMethod_setsActiveFalse() {
        when(paymentMethodRepository.findById(10L)).thenReturn(Optional.of(existingMethod));
        when(paymentMethodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentMethodService.deactivate(10L);

        assertThat(existingMethod.isActive()).isFalse();
        verify(paymentMethodRepository).save(existingMethod);
    }

    @Test
    void deactivate_nonExistingId_throwsPaymentMethodNotFoundException() {
        when(paymentMethodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentMethodService.deactivate(99L))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }
}
