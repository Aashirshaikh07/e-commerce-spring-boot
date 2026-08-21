package com.aashir.ecommerce.dto;

import java.time.LocalDateTime;

public record InventoryResponse (
        Long id,
        Long productId,
        Integer quantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
){

}

