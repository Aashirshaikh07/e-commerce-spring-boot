package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.ProductDetailsRequest;
import com.aashir.ecommerce.dto.ProductDetailsResponse;
import com.aashir.ecommerce.dto.ProductPageDetailsResponse;
import com.aashir.ecommerce.service.ProductDetailsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/{productId}/details")
public class ProductDetailsController {

    private final ProductDetailsService productDetailsService;

    public ProductDetailsController(
            ProductDetailsService productDetailsService) {
        this.productDetailsService = productDetailsService;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDetailsResponse saveDetails(
            @PathVariable Long productId,
            @Valid @RequestBody ProductDetailsRequest request) {
        return productDetailsService.saveDetails(productId, request);
    }

    @GetMapping
    public ProductDetailsResponse getDetails(
            @PathVariable Long productId) {
        return productDetailsService.getDetails(productId);
    }

    @GetMapping("/page")
    public ProductPageDetailsResponse getProductPageDetails(
            @PathVariable Long productId) {
        return productDetailsService.getProductPageDetails(productId);
    }
}