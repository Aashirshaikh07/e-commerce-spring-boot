package com.aashir.ecommerce.event;

import com.aashir.ecommerce.entity.PaymentMethod;

import java.math.BigDecimal;

public record OrderCreatedKafkaEvent(
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod
) {}

