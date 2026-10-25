package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateProductRequest;
import com.aashir.ecommerce.dto.CreateProductResponse;
import com.aashir.ecommerce.dto.UpdateProductRequest;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductStatus;
import com.aashir.ecommerce.exception.ProductNotFoundException;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceMockitoTest {
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductMixingService productMixingService;

    @Mock
    private ProductDetailsService productDetailsService;

    @Test
    void shouldCreateProduct(){

        CreateProductRequest request=new CreateProductRequest();
        request.setName("Laptop");
        request.setPrice(new BigDecimal("50000"));
        request.setDescription("Gaming Laptop");
        request.setCategory("Electronics");
        request.setStockQuantity(10);

        Product savedProduct = new Product();
        savedProduct.setId(1L);
        savedProduct.setProductName("Laptop");
        savedProduct.setPrice(new BigDecimal("50000"));
        savedProduct.setDescription("Gaming Laptop");
        savedProduct.setCategory("Electronics");
        //savedProduct.setStockQuantity(10);
        savedProduct.setStatus(ProductStatus.ACTIVE);

        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        CreateProductResponse response=productService.createProduct(request);

        assertEquals(1L,response.getId());
        assertEquals("Laptop",response.getName());
        assertEquals(new BigDecimal("50000"),response.getPrice());

        verify(productRepository,times(1)).save(any(Product.class));
    }

    @Test
    void shouldCreateProductWithCorrectDate(){
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Laptop");
        request.setPrice(new BigDecimal("50000"));
        request.setDescription("Gaming Laptop");
        request.setCategory("Electronics");
        request.setStockQuantity(10);

        Product savedProduct = new Product();
        savedProduct.setId(1L);
        savedProduct.setProductName("Laptop");
        savedProduct.setPrice(new BigDecimal("50000"));
        savedProduct.setDescription("Gaming Laptop");
        savedProduct.setCategory("Electronics");
       // savedProduct.setStockQuantity(10);
        savedProduct.setStatus(ProductStatus.ACTIVE);

        when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

        //ACT
        productService.createProduct(request);

        //VERIFY
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(captor.capture());

        Product capturedProduct = captor.getValue();

        //ASSERT
        assertEquals("Laptop", capturedProduct.getProductName());
        assertEquals(new BigDecimal("50000"), capturedProduct.getPrice());
        assertEquals("Gaming Laptop", capturedProduct.getDescription());
        assertEquals("Electronics", capturedProduct.getCategory());
       // assertEquals(10, capturedProduct.getStockQuantity());

    }
    @Test
    void shouldReturnEmptyPageWhenNoProductsExist() {
        Pageable pageable = PageRequest.of(0, 10);

        when(productRepository.findAll(pageable))
                .thenReturn(Page.empty(pageable));

        Page<CreateProductResponse> responses =
                productService.getProductsAll(pageable);

        assertTrue(responses.isEmpty());

        verify(productRepository).findAll(pageable);
    }

    @Test
    void shouldGetAllProducts(){

        Product product1 = new Product();
        product1.setId(1L);
        product1.setProductName("Laptop");
        product1.setPrice(new BigDecimal("50000"));
        product1.setCategory("Electronics");

        Product product2 = new Product();
        product2.setId(2L);
        product2.setProductName("Phone");
        product2.setPrice(new BigDecimal("30000"));
        product2.setCategory("Electronics");

        Pageable pageable = PageRequest.of(0, 10);

        when(productRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(product1, product2), pageable, 2));

        Page<CreateProductResponse> responses =
                productService.getProductsAll(pageable);

        assertEquals(2, responses.getTotalElements());

        assertEquals(1L, responses.getContent().get(0).getId());
        assertEquals("Laptop", responses.getContent().get(0).getName());
        assertEquals("Phone", responses.getContent().get(1).getName());

        verify(productRepository, times(1)).findAll(pageable);
    }

    @Test
    void shouldUpdateProduct(){
        Product existingProduct = new Product();
        existingProduct.setId(1L);
        existingProduct.setProductName("Laptop");
        existingProduct.setPrice(new BigDecimal("50000"));
        existingProduct.setCategory("Electronics");
        existingProduct.setDescription("Normal Laptop");

        UpdateProductRequest request=new UpdateProductRequest();
        request.setName("New Laptop");
        request.setPrice(new BigDecimal("60000"));
        request.setDescription("Gaming Laptop");

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(existingProduct));

        when(productRepository.save(existingProduct)).thenReturn(existingProduct);

        CreateProductResponse response = productService.updateProductById(1L,request);

        assertEquals(1L,response.getId());
        assertEquals("New Laptop",response.getName());
        assertEquals("Gaming Laptop",response.getDescription());
        assertEquals("Electronics",response.getCategory());

        verify(productRepository,times(1)).findById(1L);
        verify(productRepository).save(existingProduct);
    }

    @Test
    void shouldGetProductById(){
        Product product = new Product();
        product.setId(1L);
        product.setProductName("Laptop");
        product.setPrice(new BigDecimal("50000"));
        product.setDescription("Gaming Laptop");
        product.setCategory("Electronics");


        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));
        CreateProductResponse response= productService.getProductById(1L);
        assertEquals(1L, response.getId());
        assertEquals("Laptop", response.getName());
        assertEquals(new BigDecimal("50000"), response.getPrice());
        assertEquals("Electronics", response.getCategory());

        //This verifys method check whether service interact with it?
        verify(productRepository).findById(1L);
        verify(productRepository,times(1))
                .findById(1L);
    }

    @Test
    void shouldThrowProductNotFoundException(){
        when(productRepository.findById(88L))
                .thenReturn(Optional.empty());

     ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProductById(88L)
        );

        assertEquals("Product not found with id 88", exception.getMessage());

    }

    @Test
    void shouldThrowExceptionWhenDeletingMissingProduct() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.deleteProductById(99L)
        );

        verify(productRepository).findById(99L);

        verify(productRepository, never())
                .save(any(Product.class));
    }

}

