package com.aashir.ecommerce.dto;

import java.util.List;

public record ProductDetailsResponse(
        Long productId,
        List<String> aboutThisItem,
        List<SpecificationGroup> specifications
) {
}