package com.aashir.ecommerce.event;

import com.aashir.ecommerce.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderCancelledEvent(
        String customerName,
        String customerEmail,
        String orderNumber,
        OrderStatus status,
        List<OrderItemEvent> items,
        BigDecimal totalAmount
) {
}
