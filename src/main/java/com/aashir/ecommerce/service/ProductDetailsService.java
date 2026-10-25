package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.ProductDetailsRequest;
import com.aashir.ecommerce.dto.ProductDetailsResponse;
import com.aashir.ecommerce.dto.ProductPageDetailsResponse;
import com.aashir.ecommerce.entity.Inventory;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductDetails;
import com.aashir.ecommerce.entity.ProductStatus;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.ProductDetailsRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class ProductDetailsService {

    private final ProductDetailsRepository productDetailsRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductDetailsService(
            ProductDetailsRepository productDetailsRepository,
            ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productDetailsRepository = productDetailsRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public ProductDetailsResponse saveDetails(
            Long productId,
            ProductDetailsRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product not found with ID: " + productId));

        ProductDetails details = productDetailsRepository
                .findByProduct_Id(productId)
                .orElseGet(ProductDetails::new);

        details.setProduct(product);
        details.setAboutThisItem(request.aboutThisItem());
        details.setSpecifications(request.specifications());

        ProductDetails saved = productDetailsRepository.save(details);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductDetailsResponse getDetails(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException(
                    "Product not found with ID: " + productId);
        }

        ProductDetails details = productDetailsRepository
                .findByProduct_Id(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product details not found for ID: " + productId));

        return toResponse(details);
    }

    private ProductDetailsResponse toResponse(ProductDetails details) {
        return new ProductDetailsResponse(
                details.getProduct().getId(),
                details.getAboutThisItem() != null
                        ? details.getAboutThisItem()
                        : List.of(),
                details.getSpecifications() != null
                        ? details.getSpecifications()
                        : List.of()
        );
    }

    @Transactional(readOnly = true)
    public ProductPageDetailsResponse getProductPageDetails(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Product not found with ID: " + id));

        ProductDetails details = productDetailsRepository
                .findByProduct_Id(id)
                .orElse(null);

        Integer quantity = inventoryRepository.findByProductId(id)
                .map(Inventory::getQuantity)
                .orElse(0);

        boolean inStock = quantity > 0
                && product.getStatus() == ProductStatus.ACTIVE;

        return new ProductPageDetailsResponse(
                product.getId(),
                product.getProductName(),
                product.getPrice(),
                product.getDescription(),
                product.getImageUrl(),
                product.getCategory(),
                product.getStatus(),
                product.getCreatedAt(),
                details != null && details.getAboutThisItem() != null
                        ? details.getAboutThisItem() : List.of(),
                details != null && details.getSpecifications() != null
                        ? details.getSpecifications() : List.of(),
                quantity,
                inStock
        );
    }
}