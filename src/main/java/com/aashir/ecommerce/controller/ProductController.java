package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.dto.ProductPageDetailsResponse;
import com.aashir.ecommerce.dto.UpdateProductRequest;
import com.aashir.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;



@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Products Management",
        description = "APIs for managing Products Management"
)
public class ProductController {

    private final ProductService productService;
    
    public  ProductController(ProductService productService ){
        this.productService = productService;
    }

    @Operation(summary = "Create product")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name= "bearerAuth")
    @PostMapping("/products")
    public ResponseEntity<CreateProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ){
        CreateProductResponse product = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductPageDetailsResponse> getProductById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                productService.getProductPageDetails(id)
        );
    }

    @Operation(summary = "Update product")
    @SecurityRequirement(name= "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/products/{id}")
    public ResponseEntity<CreateProductResponse> updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request){
        CreateProductResponse updatedProduct = productService.updateProductById(id, request);

        return ResponseEntity.ok(updatedProduct);
    }


    @Operation(summary = "Delete product")
    @SecurityRequirement(name= "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/products/{id}")
    public ResponseEntity<CreateProductResponse> deleteProductById(@PathVariable Long id){
       CreateProductResponse  response= productService.deleteProductById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/products")
    public ResponseEntity<Page<CreateProductResponse>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 40) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 40"
            );
        }

        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Minimum price cannot be negative"
            );
        }

        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Maximum price cannot be negative"
            );
        }

        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "Minimum price cannot exceed maximum price"
            );
        }

        if (!"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException(
                    "Direction must be asc or desc"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<CreateProductResponse> products =
                productService.searchProducts(
                        category,
                        minPrice,
                        maxPrice,
                        keyword,
                        sortBy,
                        direction,
                        pageable
                );

        return ResponseEntity.ok(products);
    }

}
