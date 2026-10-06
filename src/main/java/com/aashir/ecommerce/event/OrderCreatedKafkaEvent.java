package com.aashir.ecommerce.event;

import com.aashir.ecommerce.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedKafkaEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod
) {}

