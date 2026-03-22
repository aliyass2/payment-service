package com.scopesky.paymentservice.service;

import com.scopesky.paymentservice.dto.paymentmethod.AddPaymentMethodRequest;
import com.scopesky.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import com.scopesky.paymentservice.exception.InvalidOperationException;
import com.scopesky.paymentservice.exception.PaymentMethodNotFoundException;
import com.scopesky.paymentservice.exception.UserNotFoundException;
import com.scopesky.paymentservice.model.PaymentMethod;
import com.scopesky.paymentservice.model.User;
import com.scopesky.paymentservice.model.enums.UserStatus;
import com.scopesky.paymentservice.repository.PaymentMethodRepository;
import com.scopesky.paymentservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final UserRepository userRepository;

    @Transactional
    public PaymentMethodResponse addPaymentMethod(AddPaymentMethodRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId()));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot add payment method to non-active user");
        }
        if (request.isDefault()) {
            unsetExistingDefault(user.getId());
        }
        PaymentMethod method = PaymentMethod.builder()
                .user(user)
                .type(request.getType())
                .provider(request.getProvider())
                .maskedIdentifier(request.getMaskedIdentifier())
                .expiryDate(request.getExpiryDate())
                .isDefault(request.isDefault())
                .active(true)
                .build();
        return toResponse(paymentMethodRepository.save(method));
    }

    public PaymentMethodResponse getPaymentMethodById(Long id) {
        return toResponse(findById(id));
    }

    public List<PaymentMethodResponse> getPaymentMethodsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return paymentMethodRepository.findByUserIdAndActiveTrue(userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PaymentMethodResponse setDefault(Long id) {
        PaymentMethod method = findById(id);
        if (!method.isActive()) {
            throw new InvalidOperationException("Cannot set an inactive payment method as default");
        }
        unsetExistingDefault(method.getUser().getId());
        method.setDefault(true);
        return toResponse(paymentMethodRepository.save(method));
    }

    @Transactional
    public void deactivate(Long id) {
        PaymentMethod method = findById(id);
        method.setActive(false);
        paymentMethodRepository.save(method);
    }

    private void unsetExistingDefault(Long userId) {
        paymentMethodRepository.findByUserIdAndIsDefaultTrueAndActiveTrue(userId)
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    paymentMethodRepository.save(existing);
                });
    }

    private PaymentMethod findById(Long id) {
        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }

    private PaymentMethodResponse toResponse(PaymentMethod method) {
        return PaymentMethodResponse.builder()
                .id(method.getId())
                .referenceId(method.getReferenceId())
                .userId(method.getUser().getId())
                .type(method.getType())
                .provider(method.getProvider())
                .maskedIdentifier(method.getMaskedIdentifier())
                .expiryDate(method.getExpiryDate())
                .isDefault(method.isDefault())
                .active(method.isActive())
                .createdAt(method.getCreatedAt())
                .build();
    }
}
