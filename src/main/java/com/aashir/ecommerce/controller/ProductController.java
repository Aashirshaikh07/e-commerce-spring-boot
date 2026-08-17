package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

    private ProductService productService;
    public  ProductController(ProductService productService){
        this.productService = productService;
    }


//    @GetMapping("/getProduct")
//    public List<CreateProductResponse> getProduct(){
//
//    }

    @PostMapping
    public ResponseEntity<CreateProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ){
        CreateProductResponse product = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }


}
