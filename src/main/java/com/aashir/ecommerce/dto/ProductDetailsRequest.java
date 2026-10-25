package com.aashir.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductDetailsRequest(

        @Size(min = 3, max = 30)
        List<@NotBlank String> aboutThisItem,
        List<SpecificationGroup> specifications

) {
}