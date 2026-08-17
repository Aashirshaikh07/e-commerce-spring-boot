package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

//    public List<CreateProductResponse> getProduct(){}

    public CreateProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();

        product.setProductName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setStockQuantity(request.getStockQuantity());

        Product savedProduct = productRepository.save(product);

        CreateProductResponse createProductResponse = new CreateProductResponse();
        createProductResponse.setId(savedProduct.getId());
        createProductResponse.setName(savedProduct.getProductName());
        createProductResponse.setPrice(savedProduct.getPrice());
        createProductResponse.setCategory(savedProduct.getCategory());
        createProductResponse.setDescription(savedProduct.getDescription());
        createProductResponse.setStockQuantity(savedProduct.getStockQuantity());
        createProductResponse.setStatus(savedProduct.getStatus());
        createProductResponse.setCreatedAt(savedProduct.getCreatedAt());
        createProductResponse.setUpdatedAt(savedProduct.getUpdatedAt());
        return createProductResponse;

    }
}
