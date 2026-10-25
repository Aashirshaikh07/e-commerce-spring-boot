package com.aashir.ecommerce.dto;

import java.util.List;

public record SpecificationGroup(
        String group,
        List<SpecificationItem> items
) {}