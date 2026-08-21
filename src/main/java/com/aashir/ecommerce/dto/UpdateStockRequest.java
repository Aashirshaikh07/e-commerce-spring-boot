package com.aashir.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStockRequest (
        @NotNull(message = "Quantity is required")
        @Min(value = 1,message = "Quantity must be greater than 0 ")
        Integer quantity
) {
}
