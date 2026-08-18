package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.dto.UpdateProductRequest;
import com.aashir.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private ProductService productService;
    public  ProductController(ProductService productService){
        this.productService = productService;
    }


    @GetMapping("/products")
    public ResponseEntity<List<CreateProductResponse>> getProducts(){
        List<CreateProductResponse> products = productService.getProductsAll();
        return  new ResponseEntity<>(products, HttpStatus.OK);
    }

    @PostMapping("/products")
    public ResponseEntity<CreateProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ){
        CreateProductResponse product = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<CreateProductResponse> getProductById(@PathVariable Long id){
        CreateProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @PatchMapping("/products/{id}")
    public ResponseEntity<CreateProductResponse> updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request){
        CreateProductResponse updatedProduct = productService.updateProductById(id, request);

        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<CreateProductResponse> deleteProductById(@PathVariable Long id){
       CreateProductResponse  response= productService.deleteProductById(id);
        return ResponseEntity.ok(response);
    }


}
