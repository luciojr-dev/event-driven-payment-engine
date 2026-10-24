package com.luciojr.payment.event;

import com.luciojr.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(
    String paymentId,
    String idempotencyKey,
    String customerId,
    BigDecimal amount,
    String currency,
    PaymentMethod paymentMethod,
    String maskedCard,
    int retryCount,
    Instant timestamp
) {
    public static PaymentEvent create(String paymentId, String idempotencyKey, String customerId,
                                      BigDecimal amount, String currency, PaymentMethod paymentMethod,
                                      String maskedCard) {
        return new PaymentEvent(
            paymentId,
            idempotencyKey,
            customerId,
            amount,
            currency,
            paymentMethod,
            maskedCard,
            0,
            Instant.now()
        );
    }
}
