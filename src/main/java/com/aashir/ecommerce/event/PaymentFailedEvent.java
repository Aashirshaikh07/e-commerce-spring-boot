package com.aashir.ecommerce.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Long paymentId,
        Long orderId,
        String reason
) {
}
