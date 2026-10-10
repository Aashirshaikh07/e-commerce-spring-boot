package com.aashir.ecommerce.dto;

public record OrderCreationResult(
        CreateOrderResponse response,
        boolean replay
) {
}
