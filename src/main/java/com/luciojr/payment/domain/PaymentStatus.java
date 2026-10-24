package com.luciojr.payment.domain;

public enum PaymentStatus {
    PROCESSING,
    APPROVED,
    REJECTED_ANTIFRAUD,
    REJECTED_INSUFFICIENT_FUNDS,
    FAILED_SENT_TO_DLQ
}
