package com.aashir.ecommerce.event;

import java.math.BigDecimal;

public record OrderItemEvent(
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
}
