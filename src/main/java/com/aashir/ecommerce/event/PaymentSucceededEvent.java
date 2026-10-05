package com.aashir.ecommerce.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Long paymentId,
        Long orderId,
        BigDecimal amount
) {
}
