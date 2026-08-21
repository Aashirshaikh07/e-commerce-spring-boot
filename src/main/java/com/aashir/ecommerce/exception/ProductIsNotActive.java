package com.aashir.ecommerce.exception;

public class ProductIsNotActive extends RuntimeException{

    public ProductIsNotActive(Long productId) {
        super("Product not active with id " + productId);
    }
}
