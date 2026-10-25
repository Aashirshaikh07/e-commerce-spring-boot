package com.aashir.ecommerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        String status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<OrderListItemResponse> items
) {
}
