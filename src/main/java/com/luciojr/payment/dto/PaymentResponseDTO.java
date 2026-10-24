package com.luciojr.payment.dto;

import com.luciojr.payment.domain.Payment;
import com.luciojr.payment.domain.PaymentMethod;
import com.luciojr.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponseDTO(
    String paymentId,
    String idempotencyKey,
    String customerId,
    BigDecimal amount,
    String currency,
    PaymentMethod paymentMethod,
    PaymentStatus status,
    String maskedCard,
    String statusReason,
    int retryCount,
    Instant createdAt,
    Instant updatedAt,
    String message
) {
    public static PaymentResponseDTO fromEntity(Payment payment, String message) {
        return new PaymentResponseDTO(
            payment.getId(),
            payment.getIdempotencyKey(),
            payment.getCustomerId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod(),
            payment.getStatus(),
            payment.getMaskedCard(),
            payment.getStatusReason(),
            payment.getRetryCount(),
            payment.getCreatedAt(),
            payment.getUpdatedAt(),
            message
        );
    }
}
