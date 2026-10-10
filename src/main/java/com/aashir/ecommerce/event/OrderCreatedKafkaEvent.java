package com.aashir.ecommerce.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedKafkaEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal totalAmount
) {}

