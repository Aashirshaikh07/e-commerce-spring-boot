package com.aashir.ecommerce.dto;

import com.aashir.ecommerce.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


public record ProductPageDetailsResponse(
        Long id,
        String productName,
        BigDecimal price,
        String description,
        String imageUrl,
        String category,
        ProductStatus status,
        LocalDateTime createdAt,
        List<String> aboutThisItem,
        List<SpecificationGroup> specifications,
        Integer stockQuantity,
        Boolean inStock
) {

}
