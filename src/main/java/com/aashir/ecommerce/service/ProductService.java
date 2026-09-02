package com.aashir.ecommerce.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.dto.UpdateProductRequest;
import com.aashir.ecommerce.entity.Inventory;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductStatus;
import com.aashir.ecommerce.exception.ProductNotFoundException;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @CacheEvict(value = "products", key = "'all'")
    public CreateProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();

        product.setProductName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());

        Product savedProduct = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(savedProduct);
        inventory.setQuantity(request.getStockQuantity());

        inventoryRepository.save(inventory);
       return mapToResponse(savedProduct);
    }

    @Cacheable(value = "products", key = "'all'")
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
            createProductResponse.setStatus(product.getStatus());
            createProductResponse.setCreatedAt(product.getCreatedAt());
            createProductResponse.setUpdatedAt(product.getUpdatedAt());
            responsesAllProduct.add(createProductResponse);
        }
        return responsesAllProduct;

    }
    @Cacheable(value = "products", key = "#id")
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
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());

        return response;
    }

    //Updating products

    @Caching(
            put = @CachePut(value = "products", key = "#id"),
            evict = @CacheEvict(value = "products", key = "'all'")
    )
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

       Product updatedProducts =  productRepository.save(product);
        return mapToResponse(updatedProducts);
    }

    @Caching(
            put = @CachePut(value = "products", key = "#id"),
            evict = @CacheEvict(value = "products", key = "'all'")
    )
    public CreateProductResponse deleteProductById(Long id){
        Product product = productRepository.findById(id)
                .orElseThrow(()->new ProductNotFoundException(id));

        product.setStatus(ProductStatus.INACTIVE);
       Product delete = productRepository.save(product);
        return mapToResponse(delete);
    }

}
