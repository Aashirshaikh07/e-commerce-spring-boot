package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductStatus;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;


import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class ProductServiceTest {

    private ProductService createService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository
    ) {
        return new ProductService(
                productRepository,
                inventoryRepository,
                mock(ProductMixingService.class),
                mock(ProductDetailsService.class)
        );
    }

    @Test
    void getProductById_shouldReturnCorrectProduct() {

        ProductRepository productRepository =
                mock(ProductRepository.class);

        InventoryRepository inventoryRepository =
                mock(InventoryRepository.class);
        ProductService productService =
                new ProductService(
                        productRepository,
                        inventoryRepository,
                        mock(ProductMixingService.class),
                        mock(ProductDetailsService.class)
                );
        Product product = new Product();
        product.setId(1L);
        product.setProductName("Laptop");
        product.setDescription("Gaming Laptop");
        product.setStatus(ProductStatus.ACTIVE);
        product.setPrice(new BigDecimal("120000.34"));
        product.setCategory("Electronics");

        Mockito.when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        //Act
        CreateProductResponse response =
                productService.getProductById(1L);

        //Assert
        assertEquals(1L, response.getId());
        assertEquals("Laptop",response.getName());
        assertEquals(new BigDecimal("120000.34"), response.getPrice());
    }

    @Test
    void  createProduct_shouldReturnCorrectProduct() {
    //Arrange
        ProductRepository productRepository =
                mock(ProductRepository.class);
        InventoryRepository inventoryRepository =
                mock(InventoryRepository.class);

        ProductService productService =
                createService(productRepository, inventoryRepository);

        CreateProductRequest request = new CreateProductRequest();

        request.setName("Laptop");
        request.setPrice(new BigDecimal("50000.00"));
        request.setDescription("Gaming Laptop");
        request.setCategory("Electronics");
        request.setStockQuantity(10);

        Product savedProduct = new Product();

        savedProduct.setId(1L);
        savedProduct.setProductName("Laptop");
        savedProduct.setPrice(new BigDecimal("50000.00"));
        savedProduct.setDescription("Gaming Laptop");
        savedProduct.setCategory("Electronics");
        savedProduct.setStatus(ProductStatus.ACTIVE);

        Mockito.when(productRepository.save(Mockito.any(Product.class)))
                .thenReturn(savedProduct);

        //Act
        CreateProductResponse response =
                productService.createProduct(request);

        assertEquals(1L, response.getId());
        assertEquals("Laptop", response.getName());
        assertEquals(new BigDecimal("50000.00"), response.getPrice());
        assertEquals("Gaming Laptop", response.getDescription());
        assertEquals("Electronics", response.getCategory());
        assertEquals(ProductStatus.ACTIVE, response.getStatus());
    }

    @Test
    void deleteProductById_shouldSetStatusToInactive() {
        ProductRepository productRepository =
                mock(ProductRepository.class);
        InventoryRepository inventoryRepository =
                mock(InventoryRepository.class);
        ProductMixingService productMixingService =
                mock(ProductMixingService.class);
        ProductDetailsService productDetailsService =
                mock(ProductDetailsService.class);
        ProductService productService =
                new ProductService(
                        productRepository,
                        inventoryRepository,
                        productMixingService,
                        productDetailsService
                );

        Product product = new Product();
        product.setId(1L);
        product.setProductName("Laptop");
        product.setDescription("Gaming Laptop");
        product.setStatus(ProductStatus.ACTIVE);
        product.setPrice(new BigDecimal("120000.34"));
        product.setCategory("Electronics");

        Mockito.when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        Mockito.when(productRepository.save(Mockito.any(Product.class)))
                .thenReturn(product);

        CreateProductResponse response =
                productService.deleteProductById(1L);

        assertEquals(1L, response.getId());
        assertEquals(ProductStatus.INACTIVE, response.getStatus());

    }
}
