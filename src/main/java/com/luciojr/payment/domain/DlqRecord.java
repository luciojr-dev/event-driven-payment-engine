package com.luciojr.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dlq_records")
public class DlqRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", length = 36, nullable = false)
    private String paymentId;

    @Column(name = "reason", length = 512, nullable = false)
    private String reason;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DlqRecord() {
    }

    public DlqRecord(String paymentId, String reason, String payload, int retryCount) {
        this.paymentId = paymentId;
        this.reason = reason;
        this.payload = payload;
        this.retryCount = retryCount;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getReason() {
        return reason;
    }

    public String getPayload() {
        return payload;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
