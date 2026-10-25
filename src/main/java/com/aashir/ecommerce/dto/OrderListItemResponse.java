package com.aashir.ecommerce.dto;

import java.math.BigDecimal;

public record OrderListItemResponse (
        Long productId,
        String productName,
        String imageUrl,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
){
}
