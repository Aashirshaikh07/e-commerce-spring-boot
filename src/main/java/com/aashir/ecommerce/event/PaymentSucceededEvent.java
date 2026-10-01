package com.aashir.ecommerce.event;

import java.math.BigDecimal;

public record PaymentSucceededEvent(
        Long paymentId,
        Long orderId,
        BigDecimal amount
) {
}
