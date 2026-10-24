package com.luciojr.payment.event;

import java.time.Instant;

public record PaymentDlqEvent(
    String paymentId,
    String idempotencyKey,
    String reason,
    int retryCount,
    Object originalPayload,
    Instant sentAt
) {
}
