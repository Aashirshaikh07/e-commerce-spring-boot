package com.aashir.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "late_payment_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LatePaymentReview {

    @Id
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private Long paymentId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal capturedAmount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public LatePaymentReview(UUID eventId, Long orderId, Long paymentId, BigDecimal capturedAmount) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.capturedAmount = capturedAmount;
        this.createdAt = Instant.now();
    }
}
