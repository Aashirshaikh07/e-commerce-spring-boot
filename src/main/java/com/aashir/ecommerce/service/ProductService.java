package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.dto.UpdateProductRequest;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductStatus;
import com.aashir.ecommerce.exception.ProductNotFoundException;
import com.aashir.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public List<CreateProductResponse> getProductsAll(){
        List<Product> products = productRepository.findAll();

        List<CreateProductResponse> responsesAllProduct = new ArrayList<>();

        for (Product product : products) {
            CreateProductResponse createProductResponse = new CreateProductResponse();
            createProductResponse.setId(product.getId());
            createProductResponse.setName(product.getProductName());
            createProductResponse.setPrice(product.getPrice());
            createProductResponse.setCategory(product.getCategory());
            createProductResponse.setDescription(product.getDescription());
            createProductResponse.setStockQuantity(product.getStockQuantity());
            createProductResponse.setStatus(product.getStatus());
            createProductResponse.setCreatedAt(product.getCreatedAt());
            createProductResponse.setUpdatedAt(product.getUpdatedAt());
            responsesAllProduct.add(createProductResponse);
        }
        return responsesAllProduct;

    }

    public CreateProductResponse getProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(()->new ProductNotFoundException(id));

        return mapToResponse(product);
    }

    private CreateProductResponse mapToResponse(Product product){
        CreateProductResponse response = new CreateProductResponse();

        response.setId(product.getId());
        response.setName(product.getProductName());
        response.setPrice(product.getPrice());
        response.setCategory(product.getCategory());
        response.setDescription(product.getDescription());
        response.setStockQuantity(product.getStockQuantity());
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        return response;
    }

    //Updating products

    public CreateProductResponse  updateProductById(Long id,UpdateProductRequest request){

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );

        if(request.getName()!=null){
            product.setProductName(request.getName());
        }
        if(request.getDescription()!=null){
            product.setDescription(request.getDescription());
        }
        if(request.getCategory()!=null){
            product.setCategory(request.getCategory());
        }
        if(request.getPrice()!=null){
            product.setPrice(request.getPrice());
        }
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }

       Product updatedProducts =  productRepository.save(product);
        return mapToResponse(updatedProducts);
    }

    public CreateProductResponse deleteProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(()->new ProductNotFoundException(id));

        product.setStatus(ProductStatus.INACTIVE);
       Product delete = productRepository.save(product);
        return mapToResponse(delete);
    }

}
