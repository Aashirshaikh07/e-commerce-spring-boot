package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.ProductDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductDetailsRepository extends JpaRepository<ProductDetails, Long> {
    Optional<ProductDetails> findByProduct_Id(Long productId);
}
